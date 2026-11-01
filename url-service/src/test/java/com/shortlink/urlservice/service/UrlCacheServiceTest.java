package com.shortlink.urlservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    private static final String KEY = UrlCacheService.KEY_PREFIX + "abc";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final AtomicLong clock = new AtomicLong();
    private UrlCacheService cache;

    @BeforeEach
    void setUp() {
        SimpleCircuitBreaker breaker = new SimpleCircuitBreaker("test", 3, Duration.ofSeconds(30), clock::get);
        cache = new UrlCacheService(redisTemplate, objectMapper, Duration.ofHours(1), Duration.ofMinutes(1), breaker);
    }

    @Test
    void getDeserializesHashDataField() throws Exception {
        CachedUrlMapping entry = new CachedUrlMapping("abc", "https://example.com", "u1", true,
                Instant.parse("2030-01-01T00:00:00Z"), 7L);
        when(redisTemplate.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.get(KEY, "d")).thenReturn(objectMapper.writeValueAsString(entry));

        assertThat(cache.get("abc")).contains(entry);
    }

    @Test
    void getMissReturnsEmpty() {
        when(redisTemplate.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.get(KEY, "d")).thenReturn(null);

        assertThat(cache.get("abc")).isEmpty();
    }

    @Test
    void putGoesThroughVersionCheckingScriptWithTtl() {
        cache.put(new CachedUrlMapping("abc", "https://example.com", "u1", true, null, 4L));

        verify(redisTemplate).execute(eq(UrlCacheService.PUT_IF_NEWER), eq(List.of(KEY)),
                eq("4"), argThat((String json) -> json.contains("\"originalUrl\":\"https://example.com\"")), eq("3600"));
    }

    @Test
    void putNotFoundUsesNegativeVersionAndShortTtl() {
        cache.putNotFound("abc");

        verify(redisTemplate).execute(eq(UrlCacheService.PUT_IF_NEWER), eq(List.of(KEY)),
                eq("-1"), anyString(), eq("60"));
    }

    @Test
    void redisErrorsAreSwallowedAndTreatedAsMiss() {
        when(redisTemplate.<Object, Object>opsForHash()).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        assertThat(cache.get("abc")).isEmpty();
        assertThatCode(() -> cache.put(new CachedUrlMapping("abc", "https://x.io", null, true, null, 1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void breakerOpensAfterConsecutiveFailuresThenProbesAfterCooldown() {
        when(redisTemplate.<Object, Object>opsForHash()).thenThrow(new RedisConnectionFailureException("down"));

        for (int i = 0; i < 3; i++) {
            cache.get("abc");
        }
        // Open: further calls don't touch Redis at all.
        for (int i = 0; i < 10; i++) {
            assertThat(cache.get("abc")).isEmpty();
        }
        verify(redisTemplate, times(3)).opsForHash();

        clock.addAndGet(Duration.ofSeconds(31).toNanos());
        cache.get("abc"); // the single half-open probe
        cache.get("abc"); // re-opened by the failed probe: skipped
        verify(redisTemplate, times(4)).opsForHash();
    }
}
