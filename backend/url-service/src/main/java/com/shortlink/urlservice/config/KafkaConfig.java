package com.shortlink.urlservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.urlservice.event.ClickEventPublisher;
import com.shortlink.urlservice.event.KafkaClickEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;

@Configuration
public class KafkaConfig {

    @Configuration
    @ConditionalOnProperty(name = "app.analytics.enabled", havingValue = "true", matchIfMissing = true)
    static class Enabled {

        /**
         * The producing service owns its topic's definition. 3 partitions
         * lets analytics-service scale to 3 consumers; replication factor
         * 1 only because the dev cluster has a single broker.
         */
        @Bean
        NewTopic linkClicksTopic(@Value("${app.analytics.topic}") String topic,
                                 @Value("${app.kafka.partitions:3}") int partitions,
                                 @Value("${app.kafka.replicas:1}") int replicas) {
            return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
        }

        /**
         * Replaces Spring Kafka's default LoggingProducerListener, which logs
         * an ERROR with the full payload for EVERY failed send: one line per
         * click while Kafka is down. KafkaClickEventPublisher already counts
         * failures and logs them at a bounded rate.
         */
        @Bean
        ProducerListener<Object, Object> kafkaProducerListener() {
            return new ProducerListener<>() { };
        }

        @Bean
        ClickEventPublisher clickEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                ObjectMapper objectMapper,
                                                @Value("${app.analytics.topic}") String topic,
                                                @Value("${app.analytics.publisher-queue-capacity:10000}") int capacity,
                                                MeterRegistry meterRegistry) {
            return new KafkaClickEventPublisher(kafkaTemplate, objectMapper, topic, capacity, meterRegistry);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.analytics.enabled", havingValue = "false")
    ClickEventPublisher noOpClickEventPublisher() {
        LoggerFactory.getLogger(KafkaConfig.class).info("app.analytics.enabled=false: click events will not be published");
        return event -> { };
    }
}
