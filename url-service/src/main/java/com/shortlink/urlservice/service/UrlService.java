package com.shortlink.urlservice.service;

import com.shortlink.urlservice.dto.CreateUrlRequest;
import com.shortlink.urlservice.dto.UpdateUrlRequest;
import com.shortlink.urlservice.exception.AliasAlreadyExistsException;
import com.shortlink.urlservice.exception.BadRequestException;
import com.shortlink.urlservice.exception.LinkExpiredException;
import com.shortlink.urlservice.exception.UrlNotFoundException;
import com.shortlink.urlservice.model.UrlMapping;
import com.shortlink.urlservice.repository.CounterRepository;
import com.shortlink.urlservice.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    /**
     * How many counter values to try when generated codes keep landing on
     * custom aliases. Each retry consumes one counter value, and aliases are
     * a vanishingly small slice of the code space, so this only ever loops
     * more than once or twice if something is badly wrong.
     */
    static final int MAX_GENERATION_ATTEMPTS = 10;

    private final UrlMappingRepository urlMappingRepository;
    private final CounterRepository counterRepository;
    private final UrlCacheService cacheService;
    private final URI publicBaseUri;
    private final Set<String> reservedAliases;

    public UrlService(UrlMappingRepository urlMappingRepository,
                      CounterRepository counterRepository,
                      UrlCacheService cacheService,
                      @Value("${app.public-base-url}") String publicBaseUrl,
                      @Value("${app.reserved-aliases:}") List<String> reservedAliases) {
        this.urlMappingRepository = urlMappingRepository;
        this.counterRepository = counterRepository;
        this.cacheService = cacheService;
        this.publicBaseUri = URI.create(publicBaseUrl);
        this.reservedAliases = reservedAliases.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Creates a new short URL owned by ownerId.
     *
     * Uniqueness is enforced by the unique index on shortCode, not by an
     * exists() pre-check: a check-then-insert lets two concurrent requests
     * for the same alias both pass the check. Instead we insert and treat
     * the duplicate-key error as the answer.
     *  - Custom alias taken: 409.
     *  - Generated code taken: the counter landed on someone's custom alias
     *    (e.g. alias "100" vs counter value 3844). Skip to the next counter
     *    value and try again.
     */
    public UrlMapping createShortUrl(CreateUrlRequest request, String ownerId) {
        rejectSelfReferencingUrl(request.originalUrl());

        UrlMapping saved;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            String alias = request.customAlias();
            if (isReserved(alias)) {
                throw new BadRequestException("customAlias '" + alias + "' is reserved");
            }
            try {
                saved = urlMappingRepository.insert(newMapping(alias, request, ownerId));
            } catch (DuplicateKeyException e) {
                throw new AliasAlreadyExistsException(alias);
            }
        } else {
            saved = insertWithGeneratedCode(request, ownerId);
        }

        // Write-through so a negative-cache entry for this code (someone
        // probed it before it existed) is replaced right away.
        cacheService.put(CachedUrlMapping.from(saved));
        return saved;
    }

    private UrlMapping insertWithGeneratedCode(CreateUrlRequest request, String ownerId) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = Base62Encoder.encode(counterRepository.getNextSequence());
            if (isReserved(code)) {
                continue;
            }
            try {
                return urlMappingRepository.insert(newMapping(code, request, ownerId));
            } catch (DuplicateKeyException e) {
                log.info("Generated code '{}' is already taken by a custom alias, trying next counter value", code);
            }
        }
        throw new IllegalStateException("Could not generate a free short code after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private UrlMapping newMapping(String shortCode, CreateUrlRequest request, String ownerId) {
        Instant now = Instant.now();
        return UrlMapping.builder()
                .shortCode(shortCode)
                .originalUrl(request.originalUrl())
                .ownerId(ownerId)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(request.expiresAt())
                .active(true)
                .build();
    }

    /**
     * Resolves a short code for the redirect path. Cache-aside: check Redis
     * first; on a miss, load from Mongo and fill the cache (or record a
     * short-lived negative entry if the code doesn't exist). UrlCacheService
     * fails open and fast, so a Redis outage degrades this to "always hit
     * Mongo", not "redirects break" or "redirects get slow".
     *
     * Returns the lightweight cached view rather than a half-populated
     * UrlMapping; callers past the redirect path use the owner-scoped
     * methods below, which always read the full record from Mongo.
     */
    public CachedUrlMapping resolve(String shortCode) {
        CachedUrlMapping entry = cacheService.get(shortCode).orElseGet(() -> loadAndCache(shortCode));

        if (entry.isNotFound() || !entry.active()) {
            throw new UrlNotFoundException(shortCode);
        }
        if (entry.isExpired()) {
            throw new LinkExpiredException(shortCode);
        }
        return entry;
    }

    private CachedUrlMapping loadAndCache(String shortCode) {
        Optional<UrlMapping> found = urlMappingRepository.findByShortCode(shortCode);
        if (found.isEmpty()) {
            cacheService.putNotFound(shortCode);
            return CachedUrlMapping.notFound(shortCode);
        }
        // Cache regardless of active/expired state: the entry carries both,
        // so a dead link also stops costing a Mongo read per request.
        CachedUrlMapping entry = CachedUrlMapping.from(found.get());
        cacheService.put(entry);
        return entry;
    }

    /**
     * Loads a link for its owner. A link owned by someone else gets the
     * same 404 as a link that doesn't exist, so the API never confirms that
     * a code is in use by another account.
     */
    public UrlMapping getOwned(String shortCode, String userId) {
        return urlMappingRepository.findByShortCode(shortCode)
                .filter(mapping -> mapping.isOwnedBy(userId))
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    public Page<UrlMapping> listOwned(String userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return urlMappingRepository.findByOwnerId(userId, pageRequest);
    }

    /**
     * Replaces a link's destination and expiry. The save is checked against
     * the @Version read here, so a concurrent edit or deactivate makes this
     * fail with OptimisticLockingFailureException (409) instead of silently
     * overwriting it.
     */
    public UrlMapping update(String shortCode, String userId, UpdateUrlRequest request) {
        rejectSelfReferencingUrl(request.originalUrl());
        UrlMapping mapping = getOwned(shortCode, userId);
        mapping.setOriginalUrl(request.originalUrl());
        mapping.setExpiresAt(request.expiresAt());
        mapping.setUpdatedAt(Instant.now());
        UrlMapping saved = urlMappingRepository.save(mapping);
        cacheService.put(CachedUrlMapping.from(saved));
        return saved;
    }

    /** Soft-deletes a link. Idempotent: deactivating twice is a no-op. */
    public void deactivate(String shortCode, String userId) {
        UrlMapping mapping = getOwned(shortCode, userId);
        if (!mapping.isActive()) {
            return;
        }
        mapping.setActive(false);
        mapping.setUpdatedAt(Instant.now());
        UrlMapping saved = urlMappingRepository.save(mapping);
        cacheService.put(CachedUrlMapping.from(saved));
    }

    private boolean isReserved(String code) {
        return reservedAliases.contains(code.toLowerCase(Locale.ROOT));
    }

    /**
     * Shortening one of our own short URLs creates a redirect loop (or a
     * chain that hides the real destination), so destinations on the
     * service's own public host and port are rejected.
     */
    private void rejectSelfReferencingUrl(String originalUrl) {
        URI target = URI.create(originalUrl);
        boolean sameHost = target.getHost() != null
                && target.getHost().equalsIgnoreCase(publicBaseUri.getHost());
        if (sameHost && effectivePort(target) == effectivePort(publicBaseUri)) {
            throw new BadRequestException("originalUrl must not point at this URL shortener");
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return Objects.equals(uri.getScheme(), "https") ? 443 : 80;
    }
}
