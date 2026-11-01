package com.shortlink.authservice.config;

import com.shortlink.authservice.model.User;
import com.shortlink.authservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issues tokens with the real encoder and validates them with the real
 * decoder, i.e. exactly what auth-service and the other services do.
 */
class JwtRoundTripTest {

    private static final String SECRET = "test-only-secret-that-is-at-least-32-bytes-long";
    private final SecurityConfig config = new SecurityConfig();

    private static User user() {
        User user = new User("alice@example.com", "hash", Instant.now());
        user.setId("user-42");
        return user;
    }

    private String issue(JwtProperties props) {
        return new TokenService(config.jwtEncoder(props), props).issue(user()).accessToken();
    }

    private JwtDecoder decoder() {
        return config.jwtDecoder(new JwtProperties(SECRET, "shortlink-auth", Duration.ofHours(1)));
    }

    @Test
    void issuedTokenValidatesAndCarriesUserIdAsSubject() {
        String token = issue(new JwtProperties(SECRET, "shortlink-auth", Duration.ofHours(1)));

        Jwt jwt = decoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo("user-42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("alice@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("shortlink-auth");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(59)));
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProperties props = new JwtProperties(SECRET, "shortlink-auth", Duration.ofHours(1));
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("shortlink-auth")
                .subject("user-42")
                .issuedAt(issuedAt)
                // Expired 2 minutes ago: beyond the decoder's default 60s clock skew.
                .expiresAt(Instant.now().minus(Duration.ofMinutes(2)))
                .build();
        String token = config.jwtEncoder(props)
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = issue(new JwtProperties(SECRET, "someone-else", Duration.ofHours(1)));

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = issue(new JwtProperties("a-completely-different-secret-of-32-bytes+", "shortlink-auth",
                Duration.ofHours(1)));

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void shortOrMissingSecretFailsFastAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "shortlink-auth", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtProperties("", "shortlink-auth", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
