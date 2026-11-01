package com.shortlink.analyticsservice.ingest;

import com.shortlink.analyticsservice.event.LinkClickedEvent;
import com.shortlink.analyticsservice.event.LinkMilestoneEvent;
import com.shortlink.analyticsservice.event.MilestonePublisher;
import com.shortlink.analyticsservice.repository.ClickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The idempotent ingestion pipeline for one click:
 *
 *  1. Create the click document with _id = eventId using op_type=create.
 *     If Kafka redelivers the event, the create fails with 409 and we stop:
 *     the click is counted exactly once, by construction.
 *  2. Only for a genuinely new click, atomically increment the link's
 *     running total and, if it lands exactly on a milestone, publish a
 *     LinkMilestone event.
 *
 * Failure windows, stated plainly: if step 2 fails after step 1 succeeded,
 * the retry sees a duplicate and skips the counter, so the running total
 * (used only for milestones) can drift one low. The stats API never reads
 * it; it counts the click documents themselves, which are exact.
 */
@Service
public class ClickIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ClickIngestionService.class);

    private final ClickRepository clickRepository;
    private final ClickEnricher enricher;
    private final MilestonePublisher milestonePublisher;
    private final Set<Long> milestones;

    public ClickIngestionService(ClickRepository clickRepository,
                                 ClickEnricher enricher,
                                 MilestonePublisher milestonePublisher,
                                 @Value("${app.milestones}") List<Long> milestones) {
        this.clickRepository = clickRepository;
        this.enricher = enricher;
        this.milestonePublisher = milestonePublisher;
        this.milestones = Set.copyOf(milestones);
    }

    public void ingest(LinkClickedEvent event) {
        boolean created = clickRepository.createIfAbsent(enricher.enrich(event));
        if (!created) {
            log.debug("Duplicate click event {} ignored", event.eventId());
            return;
        }
        long total = clickRepository.incrementCounter(event.shortCode(), event.ownerId());
        if (milestones.contains(total) && event.ownerId() != null) {
            milestonePublisher.publish(LinkMilestoneEvent.of(event.shortCode(), event.ownerId(), total, Instant.now()));
        }
    }
}
