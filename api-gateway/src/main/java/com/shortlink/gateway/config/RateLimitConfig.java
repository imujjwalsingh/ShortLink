package com.shortlink.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

    /**
     * Rate-limit bucket key: "{routeId}:{client IP}".
     *
     * The route id has to be part of the key. RedisRateLimiter stores its
     * token bucket under the resolved key ONLY (request_rate_limiter.{key}),
     * while replenish rate and burst come from whichever route is being
     * called. With an IP-only key every route shares one bucket, so the
     * strict auth limit (burst 10) caps it for everything: found in
     * docker-compose, where 12 login attempts made the next 10 redirects
     * fail with 429 although the redirect route allows a burst of 100.
     *
     * The IP is the TCP peer address, deliberately not X-Forwarded-For
     * (anyone can send that header and get a fresh bucket per request) and
     * not the JWT subject (the gateway doesn't verify tokens, so a forged
     * subject would do the same). Behind a load balancer, switch to
     * XForwardedRemoteAddressResolver.maxTrustedIndex(n) for the known
     * number of trusted proxy hops.
     */
    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(routeId(exchange) + ":" + clientIp(exchange));
    }

    static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "no-route";
    }

    static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}
