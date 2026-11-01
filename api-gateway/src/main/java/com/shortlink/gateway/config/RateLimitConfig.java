package com.shortlink.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

    /**
     * Rate-limit buckets are keyed by the TCP peer address, i.e. the client
     * as the gateway sees it. Deliberately not by X-Forwarded-For (anyone
     * can send that header and get a fresh bucket per request) and not by
     * the JWT subject (the gateway doesn't verify tokens, so a forged
     * subject would do the same). Behind a load balancer this should switch
     * to XForwardedRemoteAddressResolver.maxTrustedIndex(n) for the known
     * number of trusted proxy hops.
     */
    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String key = remote != null && remote.getAddress() != null
                    ? remote.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(key);
        };
    }
}
