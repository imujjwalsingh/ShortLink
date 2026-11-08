package com.shortlink.gateway.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisConfig {

    /**
     * The rate limiter calls Redis on every routed request. Lettuce's default,
     * after an established connection drops, is to QUEUE commands while it
     * reconnects, so each one only fails when the command timeout expires.
     * Measured in docker-compose with Redis stopped: every request through
     * the gateway took ~350 ms longer. REJECT_COMMANDS fails them immediately
     * instead, and the rate limiter's fail-open path lets the request through
     * at normal speed.
     *
     * Boot builds its own ClientOptions from spring.data.redis.* and this
     * customizer replaces them wholesale, so the connect timeout is carried
     * over explicitly. (url-service has the same customizer for its cache.)
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer failFastLettuceCustomizer(RedisProperties properties) {
        Duration connectTimeout = properties.getConnectTimeout() != null
                ? properties.getConnectTimeout()
                : Duration.ofMillis(250);
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }
}
