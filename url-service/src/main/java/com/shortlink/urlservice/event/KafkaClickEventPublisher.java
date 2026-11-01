package com.shortlink.urlservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fire-and-forget Kafka publisher for click events.
 *
 * Why a separate executor when KafkaTemplate.send() is already async:
 * send() is only async once the producer has topic metadata and buffer
 * space. When the broker is unreachable it BLOCKS the calling thread for
 * up to max.block.ms while it waits for metadata. Calling it from the
 * redirect handler would turn a Kafka outage into slow redirects. So the
 * redirect thread only enqueues onto a small bounded executor and returns.
 *
 * The queue is bounded on purpose: if Kafka is down long enough to fill
 * it, new events are dropped (and counted in shortlink.clicks.dropped)
 * rather than growing the heap until the service dies. That is the
 * explicit trade-off: analytics is best-effort, redirects are not.
 * A transactional outbox would make delivery guaranteed, at the cost of a
 * Mongo write on every redirect.
 */
public class KafkaClickEventPublisher implements ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final ExecutorService executor;
    private final Counter published;
    private final Counter failed;
    private final Counter dropped;
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong dropCount = new AtomicLong();

    public KafkaClickEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper,
                                    String topic,
                                    int queueCapacity,
                                    MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity), daemonThreads(),
                new ThreadPoolExecutor.AbortPolicy());
        this.published = meterRegistry.counter("shortlink.clicks.published");
        this.failed = meterRegistry.counter("shortlink.clicks.failed");
        this.dropped = meterRegistry.counter("shortlink.clicks.dropped");
    }

    @Override
    public void publish(LinkClickedEvent event) {
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException e) {
            dropped.increment();
            long n = dropCount.incrementAndGet();
            if (n % 1000 == 1) {
                log.warn("Click event queue full, dropping events ({} dropped so far)", n);
            }
        }
    }

    private void send(LinkClickedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.shortCode(), json).whenComplete((result, ex) -> {
                if (ex == null) {
                    published.increment();
                } else {
                    recordFailure(event, ex);
                }
            });
        } catch (Exception e) {
            // send() itself throws when metadata can't be fetched within
            // max.block.ms, i.e. the broker is down.
            recordFailure(event, e);
        }
    }

    private void recordFailure(LinkClickedEvent event, Throwable ex) {
        failed.increment();
        long n = failureCount.incrementAndGet();
        // One line per 100 failures, not one per click, while Kafka is down.
        if (n % 100 == 1) {
            log.warn("Failed to publish click event for shortCode={} ({} failures so far): {}",
                    event.shortCode(), n, ex.toString());
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("Click publisher did not drain within 5s; {} events may be lost", executor.shutdownNow().size());
        }
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger n = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, "click-publisher-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
