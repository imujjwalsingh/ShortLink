package com.shortlink.urlservice.config;

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
     * Lettuce's default, when the connection drops and auto-reconnect is on,
     * is to QUEUE commands until the connection comes back, so each one only
     * fails once the command timeout expires. On the redirect path that
     * means every request waits out the timeout during a Redis outage.
     * REJECT_COMMANDS makes them fail immediately instead, which
     * UrlCacheService treats as a miss and falls through to Mongo.
     *
     * Boot builds its own ClientOptions from spring.data.redis.* and this
     * customizer replaces them wholesale, so the connect timeout has to be
     * carried over here explicitly.
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
