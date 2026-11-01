package com.shortlink.authservice.service;

import com.shortlink.authservice.dto.LoginRequest;
import com.shortlink.authservice.dto.RegisterRequest;
import com.shortlink.authservice.dto.TokenResponse;
import com.shortlink.authservice.exception.EmailAlreadyRegisteredException;
import com.shortlink.authservice.exception.InvalidCredentialsException;
import com.shortlink.authservice.model.User;
import com.shortlink.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    // Cost 4 keeps the suite fast; production uses the default (10).
    private final BCryptPasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
        clearInvocations(passwordEncoder);
    }

    private User storedUser(String email, String rawPassword) {
        User user = new User(email, passwordEncoder.encode(rawPassword), Instant.now());
        user.setId("user-1");
        clearInvocations(passwordEncoder);
        return user;
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(userRepository.insert(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.register(new RegisterRequest("  Alice@Example.COM ", "correct-horse"));

        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPasswordHash()).startsWith("$2a$").doesNotContain("correct-horse");
        assertThat(user.isRegistrationEventPublished()).isFalse();
    }

    @Test
    void duplicateEmailIs409() {
        when(userRepository.insert(any(User.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("a@b.io", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginWithCorrectPasswordIssuesToken() {
        User user = storedUser("alice@example.com", "correct-horse");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        TokenResponse token = new TokenResponse("jwt", "Bearer", 3600);
        when(tokenService.issue(user)).thenReturn(token);

        assertThat(authService.login(new LoginRequest("ALICE@example.com", "correct-horse"))).isEqualTo(token);
    }

    @Test
    void wrongPasswordIsRejected() {
        User user = storedUser("alice@example.com", "correct-horse");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(tokenService);
    }

    @Test
    void unknownEmailStillPaysForABcryptCompare() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
        verify(passwordEncoder).matches(eq("whatever1"), startsWith("$2a$"));
        verifyNoInteractions(tokenService);
    }
}
