package com.shortlink.authservice.repository;

import com.shortlink.authservice.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    List<User> findByRegistrationEventPublishedFalseOrderByCreatedAtAsc(Pageable pageable);
}
