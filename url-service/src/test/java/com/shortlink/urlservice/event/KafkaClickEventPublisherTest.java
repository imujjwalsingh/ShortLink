package com.shortlink.urlservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaClickEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private KafkaClickEventPublisher publisher;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (publisher != null) {
            publisher.shutdown();
        }
    }

    private static LinkClickedEvent event(String code) {
        return new LinkClickedEvent("evt-" + code, code, "u1", Instant.parse("2026-11-01T10:00:00Z"),
                "https://twitter.com/", "Mozilla/5.0", "abcd");
    }

    private double counter(String name) {
        return meters.counter(name).count();
    }

    @Test
    void sendsJsonKeyedByShortCodeOffTheCallingThread() {
        publisher = new KafkaClickEventPublisher(kafkaTemplate, objectMapper, "link-clicks", 100, meters);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event("abc"));

        verify(kafkaTemplate, timeout(2000)).send(eq("link-clicks"), eq("abc"), contains("\"eventId\":\"evt-abc\""));
        await().atMost(2, TimeUnit.SECONDS).until(() -> counter("shortlink.clicks.published") == 1.0);
    }

    @Test
    void brokerFailuresAreCountedNeverThrown() {
        publisher = new KafkaClickEventPublisher(kafkaTemplate, objectMapper, "link-clicks", 100, meters);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Topic link-clicks not present in metadata after 2000 ms"));

        assertThatCode(() -> publisher.publish(event("abc"))).doesNotThrowAnyException();

        await().atMost(2, TimeUnit.SECONDS).until(() -> counter("shortlink.clicks.failed") == 1.0);
    }

    @Test
    void asyncSendFailuresAreCounted() {
        publisher = new KafkaClickEventPublisher(kafkaTemplate, objectMapper, "link-clicks", 100, meters);
        CompletableFuture<SendResult<String, String>> failed = CompletableFuture.failedFuture(new RuntimeException("boom"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        publisher.publish(event("abc"));

        await().atMost(2, TimeUnit.SECONDS).until(() -> counter("shortlink.clicks.failed") == 1.0);
    }

    @Test
    void dropsInsteadOfBlockingWhenTheQueueIsFull() throws InterruptedException {
        publisher = new KafkaClickEventPublisher(kafkaTemplate, objectMapper, "link-clicks", 1, meters);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothWorkersBusy = new CountDownLatch(2);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            bothWorkersBusy.countDown();
            release.await(5, TimeUnit.SECONDS); // simulate send() blocking on a dead broker
            return CompletableFuture.completedFuture(null);
        });

        publisher.publish(event("a"));
        publisher.publish(event("b"));
        assertThat(bothWorkersBusy.await(2, TimeUnit.SECONDS)).isTrue();
        publisher.publish(event("c")); // fills the single queue slot

        long start = System.nanoTime();
        publisher.publish(event("d")); // rejected
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isLessThan(100);
        assertThat(counter("shortlink.clicks.dropped")).isEqualTo(1.0);
        release.countDown();
    }
}
