package com.shortlink.authservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.ProducerListener;

@Configuration
public class KafkaConfig {

    /** The producing service owns its topic's definition. */
    @Bean
    NewTopic userRegisteredTopic(@Value("${app.kafka.user-registered-topic}") String topic,
                                 @Value("${app.kafka.partitions:3}") int partitions,
                                 @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Replaces Spring Kafka's default LoggingProducerListener (an ERROR with
     * the full payload, here including the user's email, for every failed
     * send). UserOutboxRelay already logs each failed attempt without it.
     */
    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new ProducerListener<>() { };
    }
}
