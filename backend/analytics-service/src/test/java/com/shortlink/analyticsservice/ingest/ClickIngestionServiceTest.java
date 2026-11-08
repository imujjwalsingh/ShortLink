package com.shortlink.analyticsservice.ingest;

import com.shortlink.analyticsservice.event.LinkClickedEvent;
import com.shortlink.analyticsservice.event.MilestonePublisher;
import com.shortlink.analyticsservice.model.ClickDocument;
import com.shortlink.analyticsservice.repository.ClickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickIngestionServiceTest {

    @Mock
    private ClickRepository clickRepository;

    @Mock
    private MilestonePublisher milestonePublisher;

    private ClickIngestionService service;

    @BeforeEach
    void setUp() {
        service = new ClickIngestionService(clickRepository, new ClickEnricher(), milestonePublisher, List.of(1L, 10L, 100L));
    }

    private static LinkClickedEvent click(String ownerId) {
        return new LinkClickedEvent("evt-1", "abc", ownerId, Instant.now(), null, "Mozilla/5.0", "vh");
    }

    @Test
    void redeliveredEventIsNotCountedTwice() {
        when(clickRepository.createIfAbsent(any(ClickDocument.class))).thenReturn(false);

        service.ingest(click("u1"));

        verify(clickRepository, never()).incrementCounter(anyString(), anyString());
        verifyNoInteractions(milestonePublisher);
    }

    @Test
    void newClickBumpsCounterWithoutMilestoneBetweenThresholds() {
        when(clickRepository.createIfAbsent(any(ClickDocument.class))).thenReturn(true);
        when(clickRepository.incrementCounter("abc", "u1")).thenReturn(42L);

        service.ingest(click("u1"));

        verifyNoInteractions(milestonePublisher);
    }

    @Test
    void landingExactlyOnAMilestonePublishesItWithADeterministicId() {
        when(clickRepository.createIfAbsent(any(ClickDocument.class))).thenReturn(true);
        when(clickRepository.incrementCounter("abc", "u1")).thenReturn(100L);

        service.ingest(click("u1"));

        verify(milestonePublisher).publish(argThat(e -> e.milestone() == 100L
                && e.ownerId().equals("u1")
                && e.eventId().equals("milestone:abc:100")));
    }

    @Test
    void linksWithoutAnOwnerNeverTriggerNotifications() {
        when(clickRepository.createIfAbsent(any(ClickDocument.class))).thenReturn(true);
        when(clickRepository.incrementCounter("abc", null)).thenReturn(1L);

        service.ingest(click(null));

        verifyNoInteractions(milestonePublisher);
    }
}
