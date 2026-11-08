package com.shortlink.authservice.controller;

import com.shortlink.authservice.config.JwtProperties;
import com.shortlink.authservice.config.SecurityConfig;
import com.shortlink.authservice.dto.LoginRequest;
import com.shortlink.authservice.dto.RegisterRequest;
import com.shortlink.authservice.dto.TokenResponse;
import com.shortlink.authservice.exception.EmailAlreadyRegisteredException;
import com.shortlink.authservice.exception.InvalidCredentialsException;
import com.shortlink.authservice.model.User;
import com.shortlink.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class,
        properties = "app.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long")
@Import(SecurityConfig.class)
@EnableConfigurationProperties(JwtProperties.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;

    private static User user() {
        User user = new User("alice@example.com", "$2a$hash", Instant.parse("2026-11-01T10:00:00Z"));
        user.setId("user-1");
        return user;
    }

    @Test
    void registerIsPublicAndNeverReturnsTheHash() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(user());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(content().string(not(containsString("hash"))));
    }

    @Test
    void registerValidatesInputWithoutEchoingThePassword() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(content().string(not(containsString("short\""))));
        verifyNoInteractions(authService);
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new TokenResponse("jwt-value", "Bearer", 3600));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void badCredentialsAre401ProblemJson() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheTokenSubjectsAccount() throws Exception {
        when(authService.findById("user-1")).thenReturn(Optional.of(user()));

        mvc.perform(get("/api/auth/me").with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }
}
