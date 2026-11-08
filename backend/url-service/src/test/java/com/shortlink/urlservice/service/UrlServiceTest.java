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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UrlService with the repositories and cache mocked: no
 * Spring context, no Mongo or Redis. The Mongo/Redis wiring itself is
 * exercised end to end by scripts/smoke-test.sh against the running stack.
 */
@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final String OWNER = "user-1";

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private CounterRepository counterRepository;

    @Mock
    private UrlCacheService cacheService;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlMappingRepository, counterRepository, cacheService,
                "http://localhost:8080", List.of("api", " Actuator "));
    }

    private void insertReturnsArgumentWithVersionZero() {
        when(urlMappingRepository.insert(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping m = invocation.getArgument(0);
            m.setVersion(0L);
            return m;
        });
    }

    private void saveBumpsVersion() {
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping m = invocation.getArgument(0);
            m.setVersion(m.getVersion() + 1);
            return m;
        });
    }

    private static UrlMapping owned(String code) {
        return UrlMapping.builder()
                .shortCode(code)
                .originalUrl("https://example.com")
                .ownerId(OWNER)
                .active(true)
                .version(3L)
                .build();
    }

    @Nested
    class Create {

        @Test
        void usesGeneratedCodeAndWritesThroughToCache() {
            when(counterRepository.getNextSequence()).thenReturn(1L);
            insertReturnsArgumentWithVersionZero();

            UrlMapping result = urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/some/long/path", null, null), OWNER);

            assertThat(result.getShortCode()).isEqualTo(Base62Encoder.encode(1L));
            assertThat(result.getOwnerId()).isEqualTo(OWNER);
            assertThat(result.getCreatedAt()).isNotNull();
            verify(cacheService).put(argThat(c -> c.shortCode().equals("1") && c.version() == 0L));
        }

        @Test
        void honorsCustomAlias() {
            insertReturnsArgumentWithVersionZero();

            UrlMapping result = urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/x", "my-alias", null), OWNER);

            assertThat(result.getShortCode()).isEqualTo("my-alias");
            verify(counterRepository, never()).getNextSequence();
        }

        @Test
        void mapsDuplicateKeyOnCustomAliasTo409() {
            when(urlMappingRepository.insert(any(UrlMapping.class)))
                    .thenThrow(new DuplicateKeyException("E11000"));

            assertThatThrownBy(() -> urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/x", "taken", null), OWNER))
                    .isInstanceOf(AliasAlreadyExistsException.class);
            verify(cacheService, never()).put(any());
        }

        @Test
        void rejectsReservedAliasCaseInsensitively() {
            assertThatThrownBy(() -> urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/x", "ACTUATOR", null), OWNER))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("reserved");
            verifyNoInteractions(urlMappingRepository);
        }

        @Test
        void skipsToNextCounterValueWhenGeneratedCodeHitsACustomAlias() {
            // Someone claimed alias "100", which is exactly base62(3844).
            assertThat(Base62Encoder.encode(3844L)).isEqualTo("100");
            when(counterRepository.getNextSequence()).thenReturn(3844L, 3845L);
            when(urlMappingRepository.insert(any(UrlMapping.class)))
                    .thenThrow(new DuplicateKeyException("E11000"))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UrlMapping result = urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/x", null, null), OWNER);

            assertThat(result.getShortCode()).isEqualTo(Base62Encoder.encode(3845L));
        }

        @Test
        void neverHandsOutAReservedWordAsAGeneratedCode() {
            long apiSeq = 36L * 62 * 62 + 51L * 62 + 44L; // base62 "api"
            assertThat(Base62Encoder.encode(apiSeq)).isEqualTo("api");
            when(counterRepository.getNextSequence()).thenReturn(apiSeq, apiSeq + 1);
            insertReturnsArgumentWithVersionZero();

            UrlMapping result = urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/x", null, null), OWNER);

            assertThat(result.getShortCode()).isEqualTo(Base62Encoder.encode(apiSeq + 1));
            verify(urlMappingRepository, times(1)).insert(any(UrlMapping.class));
        }

        @Test
        void givesUpAfterMaxAttempts() {
            when(counterRepository.getNextSequence()).thenReturn(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L);
            when(urlMappingRepository.insert(any(UrlMapping.class)))
                    .thenThrow(new DuplicateKeyException("E11000"));

            assertThatThrownBy(() -> urlService.createShortUrl(
                    new CreateUrlRequest("https://example.com/x", null, null), OWNER))
                    .isInstanceOf(IllegalStateException.class);
            verify(counterRepository, times(UrlService.MAX_GENERATION_ATTEMPTS)).getNextSequence();
        }

        @Test
        void rejectsUrlsPointingAtTheShortenerItself() {
            assertThatThrownBy(() -> urlService.createShortUrl(
                    new CreateUrlRequest("http://LOCALHOST:8080/abc", null, null), OWNER))
                    .isInstanceOf(BadRequestException.class);
            verifyNoInteractions(counterRepository, urlMappingRepository);
        }

        @Test
        void allowsSameHostOnADifferentPort() {
            when(counterRepository.getNextSequence()).thenReturn(5L);
            insertReturnsArgumentWithVersionZero();

            UrlMapping result = urlService.createShortUrl(
                    new CreateUrlRequest("http://localhost:3000/app", null, null), OWNER);

            assertThat(result.getOriginalUrl()).isEqualTo("http://localhost:3000/app");
        }
    }

    @Nested
    class Resolve {

        @Test
        void unknownCodeIsNegativelyCachedAnd404s() {
            when(cacheService.get("missing")).thenReturn(Optional.empty());
            when(urlMappingRepository.findByShortCode("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> urlService.resolve("missing"))
                    .isInstanceOf(UrlNotFoundException.class);
            verify(cacheService).putNotFound("missing");
        }

        @Test
        void negativeCacheHitSkipsMongo() {
            when(cacheService.get("missing")).thenReturn(Optional.of(CachedUrlMapping.notFound("missing")));

            assertThatThrownBy(() -> urlService.resolve("missing"))
                    .isInstanceOf(UrlNotFoundException.class);
            verifyNoInteractions(urlMappingRepository);
        }

        @Test
        void inactiveLinkIs404() {
            UrlMapping inactive = owned("abc");
            inactive.setActive(false);
            when(cacheService.get("abc")).thenReturn(Optional.empty());
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> urlService.resolve("abc"))
                    .isInstanceOf(UrlNotFoundException.class);
        }

        @Test
        void expiredLinkIs410() {
            UrlMapping expired = owned("abc");
            expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            when(cacheService.get("abc")).thenReturn(Optional.empty());
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> urlService.resolve("abc"))
                    .isInstanceOf(LinkExpiredException.class);
        }

        @Test
        void mongoHitFillsCacheWithDocumentVersion() {
            UrlMapping mapping = owned("abc");
            mapping.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
            when(cacheService.get("abc")).thenReturn(Optional.empty());
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

            CachedUrlMapping result = urlService.resolve("abc");

            assertThat(result.originalUrl()).isEqualTo("https://example.com");
            assertThat(result.ownerId()).isEqualTo(OWNER);
            verify(cacheService).put(argThat(c -> c.shortCode().equals("abc") && c.version() == 3L));
        }

        @Test
        void cacheHitSkipsMongo() {
            CachedUrlMapping cached = new CachedUrlMapping("abc", "https://example.com", OWNER, true, null, 3L);
            when(cacheService.get("abc")).thenReturn(Optional.of(cached));

            CachedUrlMapping result = urlService.resolve("abc");

            assertThat(result).isEqualTo(cached);
            verifyNoInteractions(urlMappingRepository);
        }
    }

    @Nested
    class OwnerOperations {

        @Test
        void someoneElsesLinkLooksExactlyLikeAMissingOne() {
            UrlMapping theirs = owned("abc");
            theirs.setOwnerId("user-2");
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(theirs));

            assertThatThrownBy(() -> urlService.getOwned("abc", OWNER))
                    .isInstanceOf(UrlNotFoundException.class)
                    .hasMessage(new UrlNotFoundException("abc").getMessage());
        }

        @Test
        void unownedLegacyLinkCannotBeManaged() {
            UrlMapping legacy = owned("abc");
            legacy.setOwnerId(null);
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(legacy));

            assertThatThrownBy(() -> urlService.deactivate("abc", OWNER))
                    .isInstanceOf(UrlNotFoundException.class);
        }

        @Test
        void updateSavesAndWritesNewVersionThrough() {
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(owned("abc")));
            saveBumpsVersion();
            Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS);

            UrlMapping result = urlService.update("abc", OWNER, new UpdateUrlRequest("https://new.example.com", expiry));

            assertThat(result.getOriginalUrl()).isEqualTo("https://new.example.com");
            assertThat(result.getExpiresAt()).isEqualTo(expiry);
            verify(cacheService).put(argThat(c -> c.originalUrl().equals("https://new.example.com") && c.version() == 4L));
        }

        @Test
        void updateRejectsSelfReferencingUrl() {
            assertThatThrownBy(() -> urlService.update("abc", OWNER, new UpdateUrlRequest("http://localhost:8080/x", null)))
                    .isInstanceOf(BadRequestException.class);
            verifyNoInteractions(urlMappingRepository);
        }

        @Test
        void concurrentModificationPropagatesAndLeavesCacheAlone() {
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(owned("abc")));
            when(urlMappingRepository.save(any(UrlMapping.class)))
                    .thenThrow(new OptimisticLockingFailureException("version mismatch"));

            assertThatThrownBy(() -> urlService.update("abc", OWNER, new UpdateUrlRequest("https://new.example.com", null)))
                    .isInstanceOf(OptimisticLockingFailureException.class);
            verify(cacheService, never()).put(any());
        }

        @Test
        void deactivateFlipsFlagAndWritesNewerVersionToCache() {
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(owned("abc")));
            saveBumpsVersion();

            urlService.deactivate("abc", OWNER);

            verify(urlMappingRepository).save(argThat(saved -> !saved.isActive()));
            // A newer version than any reader could have cached, so a stale
            // cache fill racing with this can't resurrect the link.
            verify(cacheService).put(argThat(c -> !c.active() && c.version() == 4L));
        }

        @Test
        void deactivateIsIdempotent() {
            UrlMapping inactive = owned("abc");
            inactive.setActive(false);
            when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(inactive));

            urlService.deactivate("abc", OWNER);

            verify(urlMappingRepository, never()).save(any());
            verifyNoInteractions(cacheService);
        }
    }
}
