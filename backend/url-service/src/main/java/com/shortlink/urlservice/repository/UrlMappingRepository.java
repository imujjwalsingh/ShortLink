package com.shortlink.urlservice.repository;

import com.shortlink.urlservice.model.UrlMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UrlMappingRepository extends MongoRepository<UrlMapping, String> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    Page<UrlMapping> findByOwnerId(String ownerId, Pageable pageable);
}
