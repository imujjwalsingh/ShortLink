package com.shortlink.notificationservice.repository;

import com.shortlink.notificationservice.model.Contact;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContactRepository extends MongoRepository<Contact, String> {
}
