package com.shortlink.analyticsservice.ingest;

import com.shortlink.analyticsservice.event.LinkClickedEvent;
import com.shortlink.analyticsservice.model.ClickDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClickEnricherTest {

    private static final String CHROME_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    private static final String EDGE_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1";
    private static final String SAFARI_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15";
    private static final String FIREFOX_LINUX = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0";
    private static final String CHROME_ANDROID_PHONE = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36";
    private static final String CHROME_ANDROID_TABLET = "Mozilla/5.0 (Linux; Android 14; SM-X910) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    private static final String SAMSUNG = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36";
    private static final String IPAD_CHROME = "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.6668.69 Mobile/15E148 Safari/604.1";
    private static final String GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    private final ClickEnricher enricher = new ClickEnricher();

    @Test
    void mapsEventToDocumentWithDerivedFieldsOnly() {
        Instant at = Instant.parse("2026-11-01T10:00:00Z");
        LinkClickedEvent event = new LinkClickedEvent("e1", "abc", "u1", at,
                "https://www.Twitter.com/some/thread", CHROME_WIN, "vh");

        ClickDocument doc = enricher.enrich(event);

        assertThat(doc).isEqualTo(new ClickDocument("e1", "abc", "u1", at, "twitter.com", "Chrome", "Windows", "desktop", "vh"));
    }

    @Test
    void botsAreLabelledSoTheyCanBeFilteredOut() {
        ClickDocument doc = enricher.enrich(new LinkClickedEvent("e1", "abc", "u1", Instant.now(), null, GOOGLEBOT, "vh"));

        assertThat(doc.browser()).isEqualTo("Bot");
        assertThat(doc.deviceType()).isEqualTo("bot");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "chrome-win      | Chrome           | Windows | desktop",
            "edge-win        | Edge             | Windows | desktop",
            "safari-iphone   | Safari           | iOS     | mobile",
            "safari-mac      | Safari           | macOS   | desktop",
            "firefox-linux   | Firefox          | Linux   | desktop",
            "android-phone   | Chrome           | Android | mobile",
            "android-tablet  | Chrome           | Android | tablet",
            "samsung         | Samsung Internet | Android | mobile",
            "ipad-chrome     | Chrome           | iOS     | tablet"
    })
    void classifiesCommonUserAgents(String name, String browser, String os, String device) {
        String ua = switch (name) {
            case "chrome-win" -> CHROME_WIN;
            case "edge-win" -> EDGE_WIN;
            case "safari-iphone" -> SAFARI_IPHONE;
            case "safari-mac" -> SAFARI_MAC;
            case "firefox-linux" -> FIREFOX_LINUX;
            case "android-phone" -> CHROME_ANDROID_PHONE;
            case "android-tablet" -> CHROME_ANDROID_TABLET;
            case "samsung" -> SAMSUNG;
            case "ipad-chrome" -> IPAD_CHROME;
            default -> throw new IllegalArgumentException(name);
        };
        assertThat(ClickEnricher.browser(ua)).isEqualTo(browser);
        assertThat(ClickEnricher.os(ua)).isEqualTo(os);
        assertThat(ClickEnricher.deviceType(ua)).isEqualTo(device);
        assertThat(ClickEnricher.isBot(ua)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {GOOGLEBOT, "curl/8.4.0", "python-requests/2.32.3", "facebookexternalhit/1.1", "Slackbot-LinkExpanding 1.0"})
    void detectsBotsAndScripts(String ua) {
        assertThat(ClickEnricher.isBot(ua)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://www.google.com/search?q=x | google.com",
            "https://news.ycombinator.com/item?id=1 | news.ycombinator.com",
            "android-app://com.slack/ | com.slack",
            "not a url at all | direct",
            "/relative/path | direct"
    })
    void extractsReferrerHost(String referrer, String expected) {
        assertThat(ClickEnricher.referrerHost(referrer)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingReferrerIsDirect(String referrer) {
        assertThat(ClickEnricher.referrerHost(referrer)).isEqualTo("direct");
    }

    @Test
    void missingUserAgentIsUnknown() {
        ClickDocument doc = enricher.enrich(new LinkClickedEvent("e1", "abc", "u1", Instant.now(), null, null, "vh"));

        assertThat(doc.browser()).isEqualTo("unknown");
        assertThat(doc.os()).isEqualTo("unknown");
        assertThat(doc.deviceType()).isEqualTo("unknown");
    }
}
