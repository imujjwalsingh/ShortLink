package com.shortlink.analyticsservice.controller;

import com.shortlink.analyticsservice.dto.LinkStatsResponse;
import com.shortlink.analyticsservice.dto.TopLinksResponse;
import com.shortlink.analyticsservice.service.AnalyticsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    static final int MAX_DAYS = 365;
    static final int MAX_LIMIT = 100;

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** Stats for one of the caller's links. */
    @GetMapping("/links/{shortCode}")
    public LinkStatsResponse linkStats(@PathVariable String shortCode,
                                       @RequestParam(defaultValue = "30") int days,
                                       @AuthenticationPrincipal Jwt jwt) {
        return analyticsService.linkStats(shortCode, jwt.getSubject(), Math.clamp(days, 1, MAX_DAYS));
    }

    /** The caller's most-clicked links. */
    @GetMapping("/links")
    public TopLinksResponse topLinks(@RequestParam(defaultValue = "10") int limit,
                                     @AuthenticationPrincipal Jwt jwt) {
        return analyticsService.topLinks(jwt.getSubject(), Math.clamp(limit, 1, MAX_LIMIT));
    }
}
