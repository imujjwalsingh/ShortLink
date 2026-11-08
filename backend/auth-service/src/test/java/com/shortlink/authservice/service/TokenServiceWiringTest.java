package com.shortlink.authservice.service;

import com.shortlink.authservice.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TokenService has a second, package-private constructor (with a Clock) for
 * tests, so Spring must be told which one to use. This builds the bean the
 * way the application does.
 */
class TokenServiceWiringTest {

    @Test
    void springCanConstructTheBean() {
        new ApplicationContextRunner()
                .withBean(JwtEncoder.class, () -> mock(JwtEncoder.class))
                .withBean(JwtProperties.class, () -> new JwtProperties(
                        "test-only-secret-that-is-at-least-32-bytes-long", "shortlink-auth", Duration.ofHours(1)))
                .withUserConfiguration(TokenService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TokenService.class);
                });
    }
}
