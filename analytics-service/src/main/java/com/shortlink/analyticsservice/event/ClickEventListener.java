package com.shortlink.analyticsservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.analyticsservice.ingest.ClickIngestionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Events travel as plain JSON strings (StringSerializer/StringDeserializer)
 * and are parsed here, rather than with Spring Kafka's JsonDeserializer and
 * type headers. That keeps producers and consumers free of each other's
 * class names, and a malformed payload fails inside the listener, where
 * the error handler can dead-letter it, instead of in the deserializer.
 */
@Component
public class ClickEventListener {

    private final ObjectMapper objectMapper;
    private final ClickIngestionService ingestionService;

    public ClickEventListener(ObjectMapper objectMapper, ClickIngestionService ingestionService) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = "${app.kafka.clicks-topic}")
    public void onClick(String payload) {
        LinkClickedEvent event;
        try {
            event = objectMapper.readValue(payload, LinkClickedEvent.class);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Unparseable click event: " + e.getOriginalMessage());
        }
        if (isBlank(event.eventId()) || isBlank(event.shortCode()) || event.occurredAt() == null) {
            throw new InvalidEventException("Click event missing eventId, shortCode or occurredAt");
        }
        ingestionService.ingest(event);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
