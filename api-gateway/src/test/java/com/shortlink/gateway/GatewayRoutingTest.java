package com.shortlink.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Boots the real gateway in front of a tiny in-process HTTP server that
 * stands in for every downstream service and echoes back what it received.
 * Redis is not running here, which also exercises the rate limiter's
 * fail-open behaviour: every request must still get through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final HttpServer downstream = startEchoServer();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + downstream.getAddress().getPort();
        registry.add("URL_SERVICE_URI", () -> base);
        registry.add("AUTH_SERVICE_URI", () -> base);
        registry.add("ANALYTICS_SERVICE_URI", () -> base);
        registry.add("REDIS_PORT", () -> "1"); // nothing listens there
    }

    @AfterAll
    static void stop() {
        downstream.stop(0);
    }

    private WebTestClient http() {
        return client.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test
    void routesApiPathsToTheirService() {
        http().get().uri("/api/urls/abc").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .startsWith("GET /api/urls/abc"));
        http().post().uri("/api/auth/login").exchange().expectStatus().isOk();
        http().get().uri("/api/analytics/links").exchange().expectStatus().isOk();
        http().get().uri("/api/urls").exchange().expectStatus().isOk();
    }

    @Test
    void routesSingleSegmentShortCodesToTheRedirect() {
        http().get().uri("/aB3_x-9").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .startsWith("GET /aB3_x-9"));
    }

    @Test
    void junkPathsAreRejectedAtTheEdge() {
        http().get().uri("/favicon.ico").exchange().expectStatus().isNotFound();
        http().get().uri("/a/b/c").exchange().expectStatus().isNotFound();
        http().post().uri("/abc").exchange().expectStatus().isNotFound();
    }

    @Test
    void clientSuppliedForwardedForIsReplacedByTheRealPeer() {
        http().get().uri("/abc").header("X-Forwarded-For", "6.6.6.6").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .doesNotContain("6.6.6.6")
                        .contains("xff=127.0.0.1"));
    }

    @Test
    void clientSuppliedHostAndForwardedHeadersAreReplaced() {
        http().get().uri("/abc")
                .header("X-Forwarded-Host", "evil.example")
                .header("Forwarded", "host=evil.example;proto=https")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .doesNotContain("evil.example")
                        .contains("xfh=localhost:")
                        .contains("fwd=proto=http;host=\"localhost:"));
    }

    @Test
    void gatewayHealthIsUpWithoutRedis() {
        http().get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    }

    private static HttpServer startEchoServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                var headers = exchange.getRequestHeaders();
                byte[] body = (exchange.getRequestMethod() + " " + exchange.getRequestURI()
                        + " xff=" + headers.getFirst("X-Forwarded-For")
                        + " xfh=" + headers.getFirst("X-Forwarded-Host")
                        + " fwd=" + headers.getFirst("Forwarded"))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
