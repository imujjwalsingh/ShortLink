package com.shortlink.urlservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside layer in front of the redirect lookup.
 *
 * Deliberately fails OPEN: any Redis error (connection refused, timeout,
 * serialization issue) is caught, logged, and treated as a cache miss —
 * never propagated to the caller. Redis is a performance optimization here,
 * not a source of truth; if it's down, redirects should keep working off
 * Mongo, just slower. A cache that can take down the redirect path defeats
 * the point of caching it.
 *
 * TTL is set on every write as a self-healing backstop: even if an
 * invalidation call is ever missed on some code path, a stale entry expires
 * on its own rather than serving bad redirects forever.
 */
@Component
public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);
    private static final String KEY_PREFIX = "shortlink:url:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public UrlCacheService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            @Value("${app.cache.ttl-seconds:3600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<CachedUrlMapping> get(String shortCode) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedUrlMapping.class));
        } catch (Exception e) {
            log.warn("Redis GET failed for shortCode={}, falling back to Mongo: {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(CachedUrlMapping mapping) {
        try {
            String json = objectMapper.writeValueAsString(mapping);
            redisTemplate.opsForValue().set(KEY_PREFIX + mapping.shortCode(), json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize cache entry for shortCode={}: {}", mapping.shortCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Redis SET failed for shortCode={}: {}", mapping.shortCode(), e.getMessage());
        }
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(KEY_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis DEL failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }
}
