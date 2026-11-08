package com.shortlink.gateway.config;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fix for a regression found in docker-compose: with Redis
 * stopped after the gateway had connected, every request paid the full
 * command timeout because Lettuce queued commands while reconnecting.
 * GatewayRoutingTest can't catch that (Redis never connects there), so
 * this pins the client options directly.
 */
class RedisConfigTest {

    @Test
    void lettuceRejectsCommandsWhileDisconnectedInsteadOfQueueingThem() {
        RedisProperties properties = new RedisProperties();
        properties.setConnectTimeout(Duration.ofMillis(250));
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();

        new RedisConfig().failFastLettuceCustomizer(properties).customize(builder);

        ClientOptions options = builder.build().getClientOptions().orElseThrow();
        assertThat(options.getDisconnectedBehavior()).isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
        assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(options.getTimeoutOptions().isTimeoutCommands()).isTrue();
    }
}
