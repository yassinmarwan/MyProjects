package com.team22.eventticketing.user.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.team22.eventticketing.user.dto.AuthResponse;
import com.team22.eventticketing.user.dto.LoginRequest;
import com.team22.eventticketing.user.dto.RegisterRequest;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.messaging.UserEventPublisher;
import com.team22.eventticketing.user.observer.EntityObserver;
import com.team22.eventticketing.user.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserEventPublisher userEventPublisher;
    private final List<EntityObserver> observers = new ArrayList<>();

    public AuthService(UserRepository userRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserEventPublisher userEventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userEventPublisher = userEventPublisher;
    }
    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }
    public void register(EntityObserver observer) {observers.add(observer);}
    public void unregister(EntityObserver observer) {observers.remove(observer);}

    // ─── [S1-F10] Register ────────────────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {

        // a) Validate fields are not blank
        if (request.name() == null || request.name().isBlank() ||
                request.email() == null || request.email().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name, email, password, and phone must not be blank");
        }

        // b) Check email and phone uniqueness
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phone already registered");
        }

        // c) Hash password
        String hashedPassword = passwordEncoder.encode(request.password());

        // d) Create user with ATTENDEE role and ACTIVE status
        User user = new User(
                request.name(),
                request.email(),
                hashedPassword,
                request.phone(),
                Role.ATTENDEE
        );
        user.setStatus(UserStatus.ACTIVE);

        try {
            user = userRepository.save(user);
        } catch (Exception e) {
            // Sequence out of sync — reset it and retry
            userRepository.resetSequence();
            user = userRepository.save(user);
        }

        // e) MongoDB logging
        notifyObservers("REGISTERED", Map.of("userId", user.getId()));
        userEventPublisher.publishUserRegistered(user.getId(), user.getEmail(), user.getRole().name());

        // f) Generate token
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        // g) Return 201
        return new AuthResponse(token, jwtService.getExpirationMs());
    }

    // ─── [S1-F11] Login ───────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {

        // a) Find user by email — 401 if not found (do not reveal whether email exists)
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // b) Verify password — 401 if wrong (same message to prevent enumeration)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // c) MongoDB logging
        notifyObservers("LOGGED_IN", Map.of("userId", user.getId()));

        // d) Generate token
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        // e) Return 200
        return new AuthResponse(token, jwtService.getExpirationMs());
    }
}