package com.shortlink.notificationservice.repository;

import com.shortlink.notificationservice.model.SentNotification;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SentNotificationRepository extends MongoRepository<SentNotification, String> {
}
