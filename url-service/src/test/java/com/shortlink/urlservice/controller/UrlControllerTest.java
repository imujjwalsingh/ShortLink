package com.shortlink.urlservice.controller;

import com.shortlink.urlservice.config.SecurityConfig;
import com.shortlink.urlservice.dto.CreateUrlRequest;
import com.shortlink.urlservice.event.ClickEventPublisher;
import com.shortlink.urlservice.event.VisitorHasher;
import com.shortlink.urlservice.exception.LinkExpiredException;
import com.shortlink.urlservice.exception.UrlNotFoundException;
import com.shortlink.urlservice.model.UrlMapping;
import com.shortlink.urlservice.service.CachedUrlMapping;
import com.shortlink.urlservice.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UrlController.class,
        properties = "app.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long")
@Import(SecurityConfig.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UrlService urlService;

    @MockBean
    private ClickEventPublisher clickEventPublisher;

    @MockBean
    private VisitorHasher visitorHasher;

    private static UrlMapping mapping(String code) {
        return UrlMapping.builder()
                .shortCode(code)
                .originalUrl("https://example.com/long")
                .ownerId("user-1")
                .createdAt(Instant.parse("2026-11-01T10:00:00Z"))
                .active(true)
                .version(0L)
                .build();
    }

    @Test
    void redirectIsPublicAndPublishesAClickEvent() throws Exception {
        when(urlService.resolve("abc"))
                .thenReturn(new CachedUrlMapping("abc", "https://example.com/long", "user-1", true, null, 0L));
        when(visitorHasher.hash(anyString(), any())).thenReturn("visitor-hash");

        mvc.perform(get("/abc")
                        .header("Referer", "https://news.ycombinator.com/")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/long"))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(visitorHasher).hash("203.0.113.7", "Mozilla/5.0");
        verify(clickEventPublisher).publish(argThat(e -> e.shortCode().equals("abc")
                && e.ownerId().equals("user-1")
                && e.referrer().equals("https://news.ycombinator.com/")
                && e.visitorHash().equals("visitor-hash")
                && e.eventId() != null));
    }

    @Test
    void unknownCodeIsProblemJson404WithNoClickEvent() throws Exception {
        when(urlService.resolve("nope")).thenThrow(new UrlNotFoundException("nope"));

        mvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No URL found for short code: nope"));
        verifyNoInteractions(clickEventPublisher);
    }

    @Test
    void expiredCodeIs410() throws Exception {
        when(urlService.resolve("old")).thenThrow(new LinkExpiredException("old"));

        mvc.perform(get("/old")).andExpect(status().isGone());
    }

    @Test
    void pathsThatCannotBeShortCodesNeverReachTheService() throws Exception {
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
        verifyNoInteractions(urlService);
    }

    @Test
    void managementApiRequiresAToken() throws Exception {
        mvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/urls/abc")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/urls/abc")).andExpect(status().isUnauthorized());
        verifyNoInteractions(urlService);
    }

    @Test
    void createUsesTokenSubjectAsOwner() throws Exception {
        when(urlService.createShortUrl(any(CreateUrlRequest.class), eq("user-1"))).thenReturn(mapping("abc"));

        mvc.perform(post("/api/urls")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/long\",\"customAlias\":\"abc\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/urls/abc"))
                .andExpect(jsonPath("$.shortCode").value("abc"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc"));
    }

    @Test
    void invalidBodyListsEveryFieldError() throws Exception {
        mvc.perform(post("/api/urls")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"ftp://x\",\"customAlias\":\"a\",\"expiresAt\":\"2001-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        verifyNoInteractions(urlService);
    }

    @Test
    void malformedJsonIsProblemJson400() throws Exception {
        mvc.perform(post("/api/urls")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void listClampsPageSize() throws Exception {
        when(urlService.listOwned("user-1", 0, UrlController.MAX_PAGE_SIZE))
                .thenReturn(new PageImpl<>(List.of(mapping("abc")), PageRequest.of(0, 100), 1));

        mvc.perform(get("/api/urls?size=5000").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shortCode").value("abc"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deleteDeactivatesForTheCaller() throws Exception {
        mvc.perform(delete("/api/urls/abc").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());
        verify(urlService).deactivate("abc", "user-1");
    }
}
