package com.shortlink.authservice.service;

import com.shortlink.authservice.dto.LoginRequest;
import com.shortlink.authservice.dto.RegisterRequest;
import com.shortlink.authservice.dto.TokenResponse;
import com.shortlink.authservice.exception.EmailAlreadyRegisteredException;
import com.shortlink.authservice.exception.InvalidCredentialsException;
import com.shortlink.authservice.model.User;
import com.shortlink.authservice.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * A real hash to compare against when the email doesn't exist, so a
     * login for an unknown email costs the same bcrypt work as a wrong
     * password. Without it, response time reveals which emails have
     * accounts.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * Same pattern as url-service: insert and let the unique index decide,
     * rather than exists-then-insert, which two concurrent sign-ups with the
     * same email could both pass.
     */
    public User register(RegisterRequest request) {
        User user = new User(normalize(request.email()), passwordEncoder.encode(request.password()), Instant.now());
        try {
            return userRepository.insert(user);
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            // One message for both cases: don't confirm which emails exist.
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(user.get());
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
