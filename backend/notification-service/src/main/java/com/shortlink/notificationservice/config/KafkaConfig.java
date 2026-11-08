package com.shortlink.notificationservice.config;

import com.shortlink.notificationservice.event.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    /**
     * The consumed topics are owned by auth-service and analytics-service,
     * but declared here too so this service never subscribes to a topic that
     * doesn't exist yet (see analytics-service KafkaConfig for why).
     */
    @Bean
    NewTopic userRegisteredTopic(@Value("${app.kafka.user-registered-topic}") String topic,
                                 @Value("${app.kafka.partitions:3}") int partitions,
                                 @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic milestonesTopic(@Value("${app.kafka.milestones-topic}") String topic,
                             @Value("${app.kafka.partitions:3}") int partitions,
                             @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic userRegisteredDeadLetterTopic(@Value("${app.kafka.user-registered-topic}") String topic,
                                           @Value("${app.kafka.partitions:3}") int partitions,
                                           @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic milestonesDeadLetterTopic(@Value("${app.kafka.milestones-topic}") String topic,
                                       @Value("${app.kafka.partitions:3}") int partitions,
                                       @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicas).build();
    }

    /**
     * Same policy as analytics-service: poison pills go straight to the DLT;
     * everything else (SMTP down, Mongo blip, contact not known yet) retries
     * with exponential backoff before being dead-lettered for replay.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          @Value("${app.kafka.retry-max-elapsed:2m}") Duration maxElapsed) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(maxElapsed.toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        // Retries are expected and transient (SMTP down, contact not known
        // yet); Spring Kafka would otherwise log every one at ERROR.
        handler.setLogLevel(KafkaException.Level.WARN);
        return handler;
    }
}
