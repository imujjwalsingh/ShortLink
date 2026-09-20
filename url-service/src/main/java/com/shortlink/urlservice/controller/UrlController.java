package com.shortlink.urlservice.controller;

import com.shortlink.urlservice.dto.CreateUrlRequest;
import com.shortlink.urlservice.dto.UrlResponse;
import com.shortlink.urlservice.model.UrlMapping;
import com.shortlink.urlservice.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UrlController {

    private final UrlService urlService;
    private final String publicBaseUrl;

    public UrlController(UrlService urlService,
                          @Value("${app.public-base-url}") String publicBaseUrl) {
        this.urlService = urlService;
        this.publicBaseUrl = publicBaseUrl;
    }

    // --- CRUD API, namespaced so it doesn't collide with short codes ---

    @PostMapping("/api/urls")
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        // ownerId hardcoded to null until Auth Service + JWT wiring (build
        // order step 7). Swap this for the authenticated principal then.
        UrlMapping mapping = urlService.createShortUrl(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(UrlResponse.from(mapping, publicBaseUrl));
    }

    @GetMapping("/api/urls/{shortCode}")
    public ResponseEntity<UrlResponse> getMetadata(@PathVariable String shortCode) {
        UrlMapping mapping = urlService.getMetadata(shortCode);
        return ResponseEntity.ok(UrlResponse.from(mapping, publicBaseUrl));
    }

    @DeleteMapping("/api/urls/{shortCode}")
    public ResponseEntity<Void> deactivate(@PathVariable String shortCode) {
        urlService.deactivate(shortCode);
        return ResponseEntity.noContent().build();
    }

    // --- Redirect: the hot path, kept dead simple on purpose ---

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        UrlMapping mapping = urlService.resolve(shortCode);

        // Step 3 note: fire the LinkClicked Kafka event here, async,
        // AFTER building the response below — never block this method on
        // the publish. A producer callback logs failures only; it must not
        // be able to fail or slow down the redirect itself.

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, mapping.getOriginalUrl())
                .build();
    }
}
