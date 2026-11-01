package com.shortlink.analyticsservice.ingest;

import com.shortlink.analyticsservice.event.LinkClickedEvent;
import com.shortlink.analyticsservice.model.ClickDocument;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Derives the dimensions the stats API groups by from the raw event.
 *
 * The user-agent classification is a deliberately small set of ordered
 * substring rules, good enough for "which browser families send traffic"
 * charts. Order matters because UAs lie for compatibility: Edge's UA
 * contains "Chrome" and "Safari", Chrome's contains "Safari", iPhone's
 * contains "Mac OS X". A full parser (ua-parser, Yauaa) is the swap-in if
 * precise versions or device models are ever needed.
 */
@Component
public class ClickEnricher {

    static final String DIRECT = "direct";
    static final String UNKNOWN = "unknown";

    private static final String[] BOT_MARKERS = {
            "bot", "crawler", "spider", "slurp", "facebookexternalhit", "embedly", "preview",
            "curl/", "wget/", "python-requests", "httpclient", "okhttp", "java/", "go-http-client", "headless"
    };

    public ClickDocument enrich(LinkClickedEvent event) {
        String ua = event.userAgent() == null ? "" : event.userAgent();
        boolean bot = isBot(ua);
        return new ClickDocument(
                event.eventId(),
                event.shortCode(),
                event.ownerId(),
                event.occurredAt(),
                referrerHost(event.referrer()),
                bot ? "Bot" : browser(ua),
                os(ua),
                bot ? "bot" : deviceType(ua),
                event.visitorHash());
    }

    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return DIRECT;
        }
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null || host.isBlank()) {
                return DIRECT;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return DIRECT;
        }
    }

    static boolean isBot(String ua) {
        String lower = ua.toLowerCase(Locale.ROOT);
        for (String marker : BOT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    static String browser(String ua) {
        if (ua.isBlank()) return UNKNOWN;
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser/")) return "Samsung Internet";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/") || ua.contains("Chromium/")) return "Chrome";
        if (ua.contains("Safari/") && ua.contains("Version/")) return "Safari";
        return "Other";
    }

    static String os(String ua) {
        if (ua.isBlank()) return UNKNOWN;
        if (ua.contains("Windows NT") || ua.contains("Windows Phone")) return "Windows";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "Other";
    }

    static String deviceType(String ua) {
        if (ua.isBlank()) return UNKNOWN;
        if (ua.contains("iPad") || ua.contains("Tablet") || (ua.contains("Android") && !ua.contains("Mobile"))) {
            return "tablet";
        }
        if (ua.contains("Mobi") || ua.contains("iPhone") || ua.contains("iPod") || ua.contains("Android")) {
            return "mobile";
        }
        return "desktop";
    }
}
