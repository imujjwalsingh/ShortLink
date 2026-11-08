package com.shortlink.urlservice.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUrlValidatorTest {

    private final HttpUrlValidator validator = new HttpUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/some/long/path?q=1&r=2#frag",
            "HTTPS://Example.COM:8443/x",
            "http://192.168.1.10/a",
            "http://[::1]:8080/",
            "https://sub.domain.example.co.uk/path%20encoded"
    })
    void acceptsAbsoluteHttpUrls(String url) {
        assertThat(validator.isValid(url, null)).as(url).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "example.com",
            "ftp://example.com/file",
            "javascript:alert(1)",
            "http://",
            "http:///path-only",
            "http://exa mple.com",
            "https://example.com/\r\nSet-Cookie: x=y",
            "https://example.com/\tx",
            "http:example.com",
            "https://bad_host.com/"
    })
    void rejectsEverythingElse(String url) {
        assertThat(validator.isValid(url, null)).as(url).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
