package com.team22.eventticketing.user.service;

import com.team22.eventticketing.user.dto.AuthResponse;
import com.team22.eventticketing.user.dto.LoginRequest;
import com.team22.eventticketing.user.dto.RegisterRequest;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.messaging.UserEventPublisher;
import com.team22.eventticketing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock UserRepository userRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserEventPublisher userEventPublisher;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, userEventPublisher);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RegisterRequest validRegisterRequest() {
        return new RegisterRequest("Alice", "alice@example.com", "secret123", "01012345678");
    }

    private User savedUser(Long id, String email) {
        User u = new User("Alice", email, "hashed", "01012345678", Role.ATTENDEE);
        u.setId(id);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    // ══════════════════════════════════════════════════════════════════════
    // [S1-F10] register
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path: register creates ACTIVE ATTENDEE user and returns JWT")
    void register_happyPath_createsUserAndReturnsToken() {
        RegisterRequest req = validRegisterRequest();
        when(userRepository.existsByEmail(req.email())).thenReturn(false);
        when(userRepository.existsByPhone(req.phone())).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(1L, req.email(), "ATTENDEE")).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(req);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(86400000L);
        verify(userRepository).save(any(User.class));
        verify(userEventPublisher).publishUserRegistered(1L, req.email(), "ATTENDEE");
    }

    @Test
    @DisplayName("register: blank name → 400 BAD_REQUEST before any repository call")
    void register_blankName_throws400() {
        RegisterRequest req = new RegisterRequest("", "alice@example.com", "secret123", "01012345678");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blank");

        verifyNoInteractions(userRepository, userEventPublisher);
    }

    @Test
    @DisplayName("register: null password → 400 BAD_REQUEST")
    void register_nullPassword_throws400() {
        RegisterRequest req = new RegisterRequest("Alice", "alice@example.com", null, "01012345678");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blank");

        verifyNoInteractions(userRepository, userEventPublisher);
    }

    @Test
    @DisplayName("register: duplicate email → 409 CONFLICT; user never saved")
    void register_duplicateEmail_throws409() {
        RegisterRequest req = validRegisterRequest();
        when(userRepository.existsByEmail(req.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userEventPublisher);
    }

    @Test
    @DisplayName("register: duplicate phone → 409 CONFLICT; user never saved")
    void register_duplicatePhone_throws409() {
        RegisterRequest req = validRegisterRequest();
        when(userRepository.existsByEmail(req.email())).thenReturn(false);
        when(userRepository.existsByPhone(req.phone())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Phone already registered");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userEventPublisher);
    }

    // ══════════════════════════════════════════════════════════════════════
    // [S1-F11] login
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path: login returns JWT for valid credentials")
    void login_happyPath_returnsToken() {
        LoginRequest req = new LoginRequest("alice@example.com", "secret123");
        User user = savedUser(1L, "alice@example.com");
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.password(), user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(1L, req.email(), "ATTENDEE")).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(req);

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(jwtService).generateToken(1L, req.email(), "ATTENDEE");
    }

    @Test
    @DisplayName("login: wrong password → 401 UNAUTHORIZED with generic message (no enumeration)")
    void login_wrongPassword_throws401() {
        LoginRequest req = new LoginRequest("alice@example.com", "wrong");
        User user = savedUser(1L, "alice@example.com");
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.password(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login: unknown email → 401 UNAUTHORIZED (same message, prevents user enumeration)")
    void login_unknownEmail_throws401() {
        LoginRequest req = new LoginRequest("unknown@example.com", "secret123");
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }
}
