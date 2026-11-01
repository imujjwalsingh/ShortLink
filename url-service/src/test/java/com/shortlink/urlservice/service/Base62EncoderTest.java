package com.shortlink.urlservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "9, 9",
            "10, A",
            "35, Z",
            "36, a",
            "61, z",
            "62, 10",
            "3844, 100",
            "9223372036854775807, AzL8n0Y58m7"
    })
    void encodesKnownValues(long value, String expected) {
        assertThat(Base62Encoder.encode(value)).isEqualTo(expected);
    }

    @Test
    void isInjectiveOverARange() {
        Set<String> seen = new HashSet<>();
        for (long i = 0; i < 100_000; i++) {
            assertThat(seen.add(Base62Encoder.encode(i))).isTrue();
        }
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
