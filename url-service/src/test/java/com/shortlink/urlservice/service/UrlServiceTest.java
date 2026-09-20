package com.shortlink.urlservice.service;

import com.shortlink.urlservice.dto.CreateUrlRequest;
import com.shortlink.urlservice.exception.AliasAlreadyExistsException;
import com.shortlink.urlservice.exception.LinkExpiredException;
import com.shortlink.urlservice.exception.UrlNotFoundException;
import com.shortlink.urlservice.model.UrlMapping;
import com.shortlink.urlservice.repository.CounterRepository;
import com.shortlink.urlservice.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UrlService with the repository layer mocked — no Spring
 * context, no real (or embedded) Mongo required. Fast and dependency-free,
 * which is the right level for testing business logic; the Mongo/Redis
 * wiring itself gets exercised manually via docker-compose + curl (see
 * README) and later by a slimmer @DataMongoTest suite once Testcontainers
 * is introduced for Step 2.
 */
@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private CounterRepository counterRepository;

    @Mock
    private UrlCacheService cacheService;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlMappingRepository, counterRepository, cacheService);
    }

    @Test
    void createsShortUrlUsingGeneratedCode() {
        when(counterRepository.getNextSequence()).thenReturn(1L);
        when(urlMappingRepository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UrlMapping result = urlService.createShortUrl(
                new CreateUrlRequest("https://example.com/some/long/path", null, null), null);

        assertThat(result.getShortCode()).isEqualTo(Base62Encoder.encode(1L));
        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com/some/long/path");
        verify(urlMappingRepository, never()).existsByShortCode(any());
    }

    @Test
    void honorsCustomAliasWhenAvailable() {
        when(urlMappingRepository.existsByShortCode("my-alias")).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UrlMapping result = urlService.createShortUrl(
                new CreateUrlRequest("https://example.com/x", "my-alias", null), null);

        assertThat(result.getShortCode()).isEqualTo("my-alias");
        verify(counterRepository, never()).getNextSequence();
    }

    @Test
    void rejectsTakenCustomAlias() {
        when(urlMappingRepository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> urlService.createShortUrl(
                new CreateUrlRequest("https://example.com/x", "taken", null), null))
                .isInstanceOf(AliasAlreadyExistsException.class);

        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void resolveThrowsWhenShortCodeUnknown() {
        when(urlMappingRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolve("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveThrowsWhenLinkInactive() {
        UrlMapping inactive = UrlMapping.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .active(false)
                .build();
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> urlService.resolve("abc"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveThrowsWhenLinkExpired() {
        UrlMapping expired = UrlMapping.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .active(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> urlService.resolve("abc"))
                .isInstanceOf(LinkExpiredException.class);
    }

    @Test
    void resolveReturnsMappingWhenActiveAndNotExpired() {
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .active(true)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        UrlMapping result = urlService.resolve("abc");

        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void resolveReturnsFromCacheWithoutHittingMongoOnHit() {
        CachedUrlMapping cached = new CachedUrlMapping("abc", "https://example.com", true, null);
        when(cacheService.get("abc")).thenReturn(Optional.of(cached));

        UrlMapping result = urlService.resolve("abc");

        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
        verify(urlMappingRepository, never()).findByShortCode(any());
    }

    @Test
    void resolvePopulatesCacheOnMongoLookup() {
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .active(true)
                .build();
        when(cacheService.get("abc")).thenReturn(Optional.empty());
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        urlService.resolve("abc");

        verify(cacheService).put(argThat(c -> c.shortCode().equals("abc")
                && c.originalUrl().equals("https://example.com")));
    }

    @Test
    void deactivateFlipsActiveFlagAndSaves() {
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .active(true)
                .build();
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(mapping));
        when(urlMappingRepository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        urlService.deactivate("abc");

        verify(urlMappingRepository).save(argThat(saved -> !saved.isActive()));
        verify(cacheService).evict("abc");
    }
}
