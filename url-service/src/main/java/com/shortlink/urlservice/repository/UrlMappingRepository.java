package com.shortlink.urlservice.repository;

import com.shortlink.urlservice.model.UrlMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UrlMappingRepository extends MongoRepository<UrlMapping, String> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
