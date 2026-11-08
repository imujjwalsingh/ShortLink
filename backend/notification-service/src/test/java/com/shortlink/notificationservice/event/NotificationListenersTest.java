package com.shortlink.notificationservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationListenersTest {

    @Mock
    private NotificationService notificationService;

    private NotificationListeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new NotificationListeners(new ObjectMapper().findAndRegisterModules(), notificationService);
    }

    @Test
    void parsesUserRegistered() {
        listeners.onUserRegistered("""
                {"eventId":"user-registered:u1","userId":"u1","email":"a@b.io","occurredAt":"2026-11-01T10:00:00Z","v":2}
                """);

        verify(notificationService).handleUserRegistered(argThat(e -> e.email().equals("a@b.io")));
    }

    @Test
    void parsesMilestone() {
        listeners.onMilestone("""
                {"eventId":"milestone:abc:10","shortCode":"abc","ownerId":"u1","milestone":10,"occurredAt":"2026-11-01T10:00:00Z"}
                """);

        verify(notificationService).handleMilestone(argThat(e -> e.milestone() == 10L));
    }

    @Test
    void poisonPillsAreNonRetryable() {
        assertThatThrownBy(() -> listeners.onUserRegistered("garbage")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> listeners.onMilestone("{\"shortCode\":\"abc\"}")).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(notificationService);
    }
}
