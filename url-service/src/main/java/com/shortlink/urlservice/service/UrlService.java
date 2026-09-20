package com.shortlink.urlservice.service;

import com.shortlink.urlservice.dto.CreateUrlRequest;
import com.shortlink.urlservice.exception.AliasAlreadyExistsException;
import com.shortlink.urlservice.exception.LinkExpiredException;
import com.shortlink.urlservice.exception.UrlNotFoundException;
import com.shortlink.urlservice.model.UrlMapping;
import com.shortlink.urlservice.repository.CounterRepository;
import com.shortlink.urlservice.repository.UrlMappingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class UrlService {

    private final UrlMappingRepository urlMappingRepository;
    private final CounterRepository counterRepository;
    private final UrlCacheService cacheService;

    public UrlService(UrlMappingRepository urlMappingRepository,
                       CounterRepository counterRepository,
                       UrlCacheService cacheService) {
        this.urlMappingRepository = urlMappingRepository;
        this.counterRepository = counterRepository;
        this.cacheService = cacheService;
    }

    /**
     * Creates a new short URL. If a customAlias is given, it takes priority
     * over the generated code, but still has to pass the uniqueness check
     * (the unique index on shortCode is the real backstop here — this
     * existsBy check is just for a friendlier error before hitting a
     * duplicate-key exception).
     */
    public UrlMapping createShortUrl(CreateUrlRequest request, String ownerId) {
        String shortCode;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            if (urlMappingRepository.existsByShortCode(request.customAlias())) {
                throw new AliasAlreadyExistsException(request.customAlias());
            }
            shortCode = request.customAlias();
        } else {
            shortCode = Base62Encoder.encode(counterRepository.getNextSequence());
        }

        UrlMapping mapping = UrlMapping.builder()
                .shortCode(shortCode)
                .originalUrl(request.originalUrl())
                .ownerId(ownerId)
                .createdAt(Instant.now())
                .expiresAt(request.expiresAt())
                .active(true)
                .build();

        return urlMappingRepository.save(mapping);
    }

    /**
     * Resolves a short code to its mapping for the redirect path.
     * Cache-aside: check Redis first; on miss, hit Mongo and populate the
     * cache before returning. UrlCacheService fails open on any Redis
     * error, so a Redis outage degrades this to "always hit Mongo", not
     * "redirects break".
     *
     * On a cache hit, only the fields the redirect actually needs
     * (shortCode/originalUrl/active/expiresAt) are populated on the
     * returned UrlMapping — id/ownerId/createdAt are intentionally left
     * null. Callers past the redirect path should use getMetadata()
     * instead, which always goes to Mongo for the full record.
     */
    public UrlMapping resolve(String shortCode) {
        Optional<CachedUrlMapping> cached = cacheService.get(shortCode);
        if (cached.isPresent()) {
            return fromCacheOrThrow(shortCode, cached.get());
        }

        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Cache regardless of active/expired state — a soon-to-expire or
        // just-deactivated link still benefits from not re-hitting Mongo on
        // every retry; the TTL and explicit evict() on deactivate() keep it
        // from going stale for long.
        cacheService.put(CachedUrlMapping.from(mapping));

        if (!mapping.isActive()) {
            throw new UrlNotFoundException(shortCode);
        }
        if (mapping.isExpired()) {
            throw new LinkExpiredException(shortCode);
        }
        return mapping;
    }

    private UrlMapping fromCacheOrThrow(String shortCode, CachedUrlMapping cached) {
        if (!cached.active()) {
            throw new UrlNotFoundException(shortCode);
        }
        if (cached.isExpired()) {
            throw new LinkExpiredException(shortCode);
        }
        return UrlMapping.builder()
                .shortCode(cached.shortCode())
                .originalUrl(cached.originalUrl())
                .active(cached.active())
                .expiresAt(cached.expiresAt())
                .build();
    }

    public UrlMapping getMetadata(String shortCode) {
        return urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    public void deactivate(String shortCode) {
        UrlMapping mapping = getMetadata(shortCode);
        mapping.setActive(false);
        urlMappingRepository.save(mapping);
        cacheService.evict(shortCode);
    }
}
