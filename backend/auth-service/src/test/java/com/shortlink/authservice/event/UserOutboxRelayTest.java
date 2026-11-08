package com.shortlink.authservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.authservice.model.User;
import com.shortlink.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOutboxRelayTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private UserOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new UserOutboxRelay(userRepository, mongoTemplate, kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(), "user-registered", 100);
    }

    private static User user(String id) {
        User user = new User(id + "@example.com", "hash", Instant.parse("2026-11-01T10:00:00Z"));
        user.setId(id);
        return user;
    }

    @Test
    void publishesEachPendingUserAndMarksItOnlyAfterTheBrokerAcks() {
        when(userRepository.findByRegistrationEventPublishedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(user("u1"), user("u2")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relayPendingRegistrations();

        verify(kafkaTemplate).send(eq("user-registered"), eq("u1"), contains("\"eventId\":\"user-registered:u1\""));
        verify(kafkaTemplate).send(eq("user-registered"), eq("u2"), contains("\"email\":\"u2@example.com\""));
        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(2)).updateFirst(queries.capture(), any(Update.class), eq(User.class));
        assertThat(queries.getAllValues()).extracting(q -> q.getQueryObject().get("_id")).containsExactly("u1", "u2");
    }

    @Test
    void stopsAtFirstFailureWithoutMarkingAnything() {
        when(userRepository.findByRegistrationEventPublishedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(user("u1"), user("u2")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.relayPendingRegistrations();

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }
}
