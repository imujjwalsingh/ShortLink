package com.shortlink.urlservice.controller;

import com.shortlink.urlservice.dto.CreateUrlRequest;
import com.shortlink.urlservice.dto.PageResponse;
import com.shortlink.urlservice.dto.UpdateUrlRequest;
import com.shortlink.urlservice.dto.UrlResponse;
import com.shortlink.urlservice.event.ClickEventPublisher;
import com.shortlink.urlservice.event.LinkClickedEvent;
import com.shortlink.urlservice.event.VisitorHasher;
import com.shortlink.urlservice.model.UrlMapping;
import com.shortlink.urlservice.service.CachedUrlMapping;
import com.shortlink.urlservice.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
public class UrlController {

    static final int MAX_PAGE_SIZE = 100;

    private final UrlService urlService;
    private final ClickEventPublisher clickEventPublisher;
    private final VisitorHasher visitorHasher;
    private final String publicBaseUrl;

    public UrlController(UrlService urlService,
                         ClickEventPublisher clickEventPublisher,
                         VisitorHasher visitorHasher,
                         @Value("${app.public-base-url}") String publicBaseUrl) {
        this.urlService = urlService;
        this.clickEventPublisher = clickEventPublisher;
        this.visitorHasher = visitorHasher;
        this.publicBaseUrl = publicBaseUrl;
    }

    // --- Management API: namespaced under /api, owner-only, JWT required ---

    @PostMapping("/api/urls")
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        UrlMapping mapping = urlService.createShortUrl(request, jwt.getSubject());
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{code}").buildAndExpand(mapping.getShortCode()).toUri())
                .body(UrlResponse.from(mapping, publicBaseUrl));
    }

    @GetMapping("/api/urls")
    public PageResponse<UrlResponse> listMyUrls(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @AuthenticationPrincipal Jwt jwt) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageResponse.from(urlService.listOwned(jwt.getSubject(), safePage, safeSize),
                mapping -> UrlResponse.from(mapping, publicBaseUrl));
    }

    @GetMapping("/api/urls/{shortCode}")
    public UrlResponse getMetadata(@PathVariable String shortCode, @AuthenticationPrincipal Jwt jwt) {
        return UrlResponse.from(urlService.getOwned(shortCode, jwt.getSubject()), publicBaseUrl);
    }

    @PutMapping("/api/urls/{shortCode}")
    public UrlResponse update(@PathVariable String shortCode,
                              @Valid @RequestBody UpdateUrlRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return UrlResponse.from(urlService.update(shortCode, jwt.getSubject(), request), publicBaseUrl);
    }

    @DeleteMapping("/api/urls/{shortCode}")
    public ResponseEntity<Void> deactivate(@PathVariable String shortCode, @AuthenticationPrincipal Jwt jwt) {
        urlService.deactivate(shortCode, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    // --- Redirect: the public hot path, kept dead simple on purpose ---

    /**
     * The path pattern only admits characters a short code can contain, so
     * requests like /favicon.ico or /robots.txt never reach Redis or Mongo.
     */
    @GetMapping("/{shortCode:[A-Za-z0-9_-]{1,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        CachedUrlMapping link = urlService.resolve(shortCode);

        // Enqueue only; never blocks or throws (see KafkaClickEventPublisher).
        clickEventPublisher.publish(clickEvent(link, request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, link.originalUrl())
                // Keep browsers and CDNs from caching the redirect, so every
                // click reaches us (and is counted), and an edit or
                // deactivate takes effect immediately.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private LinkClickedEvent clickEvent(CachedUrlMapping link, HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return new LinkClickedEvent(
                UUID.randomUUID().toString(),
                link.shortCode(),
                link.ownerId(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                request.getHeader(HttpHeaders.REFERER),
                userAgent,
                visitorHasher.hash(clientIp(request), userAgent));
    }

    /**
     * First hop of X-Forwarded-For (set by the gateway), else the socket
     * address. Only trustworthy because the services aren't reachable
     * except through the gateway in the compose setup; it only feeds the
     * visitor hash, never an access decision.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
