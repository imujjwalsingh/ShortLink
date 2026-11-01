package com.shortlink.analyticsservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MilestonePublisher {

    private static final Logger log = LoggerFactory.getLogger(MilestonePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public MilestonePublisher(KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${app.kafka.milestones-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    /**
     * Best-effort: a failed send is logged, not retried. Milestone emails
     * are a nice-to-have, and retrying here would mean re-processing the
     * click, which the dedupe step would then (correctly) skip.
     */
    public void publish(LinkMilestoneEvent event) {
        try {
            kafkaTemplate.send(topic, event.shortCode(), objectMapper.writeValueAsString(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish milestone {} for {}: {}", event.milestone(), event.shortCode(), ex.toString());
                        } else {
                            log.info("Link {} reached {} clicks", event.shortCode(), event.milestone());
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize milestone event", e);
        } catch (RuntimeException e) {
            log.warn("Failed to publish milestone {} for {}: {}", event.milestone(), event.shortCode(), e.toString());
        }
    }
}
