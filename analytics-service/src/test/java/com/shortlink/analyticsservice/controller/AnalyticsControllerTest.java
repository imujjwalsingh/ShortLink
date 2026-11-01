package com.shortlink.analyticsservice.controller;

import com.shortlink.analyticsservice.config.SecurityConfig;
import com.shortlink.analyticsservice.dto.CountBucket;
import com.shortlink.analyticsservice.dto.DailyCount;
import com.shortlink.analyticsservice.dto.LinkClicks;
import com.shortlink.analyticsservice.dto.LinkStatsResponse;
import com.shortlink.analyticsservice.dto.TopLinksResponse;
import com.shortlink.analyticsservice.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class,
        properties = "app.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long")
@Import(SecurityConfig.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void requiresAToken() throws Exception {
        mvc.perform(get("/api/analytics/links/abc")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/links")).andExpect(status().isUnauthorized());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void statsAreScopedToTheCallerAndDaysAreClamped() throws Exception {
        LinkStatsResponse stats = new LinkStatsResponse("abc", 12, 7, 365,
                List.of(new DailyCount("2026-11-01", 12)),
                List.of(new CountBucket("twitter.com", 8)),
                List.of(new CountBucket("Chrome", 12)),
                List.of(new CountBucket("Windows", 12)),
                List.of(new CountBucket("desktop", 12)));
        when(analyticsService.linkStats("abc", "user-1", 365)).thenReturn(stats);

        mvc.perform(get("/api/analytics/links/abc?days=9999").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(12))
                .andExpect(jsonPath("$.uniqueVisitors").value(7))
                .andExpect(jsonPath("$.clicksPerDay[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.topReferrers[0].value").value("twitter.com"));
    }

    @Test
    void topLinksForTheCaller() throws Exception {
        when(analyticsService.topLinks("user-1", 10))
                .thenReturn(new TopLinksResponse(20, List.of(new LinkClicks("abc", 15), new LinkClicks("xyz", 5))));

        mvc.perform(get("/api/analytics/links").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(20))
                .andExpect(jsonPath("$.links[0].shortCode").value("abc"));
    }

    @Test
    void elasticsearchOutageIs503NotA500() throws Exception {
        when(analyticsService.linkStats("abc", "user-1", 30))
                .thenThrow(new UncheckedIOException(new IOException("Connection refused")));

        mvc.perform(get("/api/analytics/links/abc").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
