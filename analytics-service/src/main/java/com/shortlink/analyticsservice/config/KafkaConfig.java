package com.shortlink.analyticsservice.config;

import com.shortlink.analyticsservice.event.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    /**
     * url-service owns link-clicks, but it is declared here too (same
     * partition count, so creation is an idempotent no-op for whichever
     * service starts second). A consumer that subscribes before its topic
     * exists otherwise sits idle until its next metadata refresh, up to 5
     * minutes later.
     */
    @Bean
    NewTopic linkClicksTopic(@Value("${app.kafka.clicks-topic}") String topic,
                             @Value("${app.kafka.partitions:3}") int partitions,
                             @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    /** analytics-service produces milestones, so it owns that topic. */
    @Bean
    NewTopic linkMilestonesTopic(@Value("${app.kafka.milestones-topic}") String topic,
                                 @Value("${app.kafka.partitions:3}") int partitions,
                                 @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Dead-letter topic for click events. The recoverer publishes to the same
     * partition number as the source record, so it needs at least as many
     * partitions as link-clicks.
     */
    @Bean
    NewTopic linkClicksDeadLetterTopic(@Value("${app.kafka.clicks-topic}") String topic,
                                       @Value("${app.kafka.partitions:3}") int partitions,
                                       @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicas).build();
    }

    /**
     * Retry policy for the click listener (Spring Boot wires this bean into
     * the listener container factory automatically):
     *  - InvalidEventException (malformed or incomplete event): retrying
     *    can't help, so dead-letter it immediately.
     *  - Anything else, typically Elasticsearch being unreachable: retry in
     *    place with exponential backoff (1s, 2s, 4s ... capped at 30s) so a
     *    short outage loses nothing and keeps per-partition order. If it
     *    still fails after retry-max-elapsed, dead-letter it so one stuck
     *    record can't block its partition forever. DLT records keep the
     *    original payload plus exception headers and can be replayed.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          @Value("${app.kafka.retry-max-elapsed:2m}") Duration maxElapsed) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(maxElapsed.toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
