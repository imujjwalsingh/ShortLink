package com.shortlink.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final KeyResolver resolver = new RateLimitConfig().clientIpKeyResolver();

    private static MockServerWebExchange exchange(String routeId, String ip, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/abc")
                .remoteAddress(new InetSocketAddress(ip, 54321));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id(routeId).uri("http://localhost:8081").predicate(e -> true).build());
        return exchange;
    }

    @Test
    void sameClientGetsASeparateBucketPerRoute() {
        // Regression: with an IP-only key, RedisRateLimiter shared one bucket
        // across routes and the auth route's burst of 10 throttled redirects.
        String auth = resolver.resolve(exchange("auth", "203.0.113.7", null)).block();
        String redirect = resolver.resolve(exchange("redirect", "203.0.113.7", null)).block();

        assertThat(auth).isEqualTo("auth:203.0.113.7");
        assertThat(redirect).isEqualTo("redirect:203.0.113.7");
        assertThat(auth).isNotEqualTo(redirect);
    }

    @Test
    void differentClientsOnTheSameRouteGetDifferentBuckets() {
        assertThat(resolver.resolve(exchange("urls", "203.0.113.7", null)).block())
                .isNotEqualTo(resolver.resolve(exchange("urls", "198.51.100.9", null)).block());
    }

    @Test
    void forwardedForCannotBeUsedToGetAFreshBucket() {
        assertThat(resolver.resolve(exchange("auth", "203.0.113.7", "1.2.3.4")).block())
                .isEqualTo("auth:203.0.113.7");
    }
}
