package com.shortlink.urlservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * UrlCacheService has a second, package-private constructor for tests, so
 * Spring must be told which one to use. The unit tests call constructors
 * directly and can't catch that; this builds the bean the way the
 * application does.
 */
class UrlCacheServiceWiringTest {

    @Test
    void springCanConstructTheBean() {
        new ApplicationContextRunner()
                // SpringApplication installs this in the real app; it's what
                // turns "30s" into a Duration for @Value.
                .withInitializer(ctx -> ctx.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class)
                .withUserConfiguration(UrlCacheService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UrlCacheService.class);
                });
    }
}
