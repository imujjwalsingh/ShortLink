package com.shortlink.analyticsservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.analyticsservice.ingest.ClickIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClickEventListenerTest {

    @Mock
    private ClickIngestionService ingestionService;

    private ClickEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ClickEventListener(new ObjectMapper().findAndRegisterModules(), ingestionService);
    }

    @Test
    void parsesPayloadAndIgnoresUnknownFields() {
        listener.onClick("""
                {"eventId":"e1","shortCode":"abc","ownerId":"u1","occurredAt":"2026-11-01T10:00:00.123Z",
                 "referrer":null,"userAgent":"Mozilla/5.0","visitorHash":"vh","someFutureField":42}
                """);

        verify(ingestionService).ingest(argThat(e -> e.eventId().equals("e1")
                && e.occurredAt().equals(Instant.parse("2026-11-01T10:00:00.123Z"))));
    }

    @Test
    void malformedJsonIsANonRetryablePoisonPill() {
        assertThatThrownBy(() -> listener.onClick("{not json"))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(ingestionService);
    }

    @Test
    void eventWithoutIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> listener.onClick("{\"shortCode\":\"abc\",\"occurredAt\":\"2026-11-01T10:00:00Z\"}"))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventId");
        verifyNoInteractions(ingestionService);
    }
}
