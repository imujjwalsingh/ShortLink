package com.shortlink.notificationservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.notificationservice.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListeners {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationListeners(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.kafka.user-registered-topic}")
    public void onUserRegistered(String payload) {
        UserRegisteredEvent event = parse(payload, UserRegisteredEvent.class);
        if (isBlank(event.eventId()) || isBlank(event.userId()) || isBlank(event.email())) {
            throw new InvalidEventException("UserRegistered event missing eventId, userId or email");
        }
        notificationService.handleUserRegistered(event);
    }

    @KafkaListener(topics = "${app.kafka.milestones-topic}")
    public void onMilestone(String payload) {
        LinkMilestoneEvent event = parse(payload, LinkMilestoneEvent.class);
        if (isBlank(event.eventId()) || isBlank(event.shortCode()) || isBlank(event.ownerId())) {
            throw new InvalidEventException("LinkMilestone event missing eventId, shortCode or ownerId");
        }
        notificationService.handleMilestone(event);
    }

    private <T> T parse(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Unparseable " + type.getSimpleName() + ": " + e.getOriginalMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
