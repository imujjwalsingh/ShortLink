package com.shortlink.authservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.authservice.model.User;
import com.shortlink.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Polls for users whose UserRegistered event hasn't been acknowledged by
 * Kafka yet, publishes them in sign-up order, and marks each one done only
 * after the broker acks. Delivery is at-least-once: a crash between the ack
 * and the flag update republishes that event, which consumers dedupe on its
 * deterministic eventId. With several auth-service instances, each one may
 * relay the same pending users concurrently; the same dedupe covers that.
 *
 * On the first failure it stops for this round instead of hammering a
 * broker that is down; the next poll retries from the oldest pending user.
 */
@Component
public class UserOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(UserOutboxRelay.class);

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int batchSize;

    public UserOutboxRelay(UserRepository userRepository,
                           MongoTemplate mongoTemplate,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${app.kafka.user-registered-topic}") String topic,
                           @Value("${app.outbox.batch-size:100}") int batchSize) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}", initialDelay = 1000)
    public void relayPendingRegistrations() {
        List<User> pending;
        try {
            pending = userRepository.findByRegistrationEventPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
        } catch (Exception e) {
            log.warn("Outbox poll failed: {}", e.toString());
            return;
        }
        for (User user : pending) {
            try {
                UserRegisteredEvent event = UserRegisteredEvent.of(user.getId(), user.getEmail(), user.getCreatedAt());
                kafkaTemplate.send(topic, user.getId(), objectMapper.writeValueAsString(event))
                        .get(10, TimeUnit.SECONDS);
                mongoTemplate.updateFirst(
                        Query.query(where("_id").is(user.getId())),
                        Update.update("registrationEventPublished", true),
                        User.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Could not publish UserRegistered for user {} ({} pending); will retry: {}",
                        user.getId(), pending.size(), e.toString());
                return;
            }
        }
    }
}
