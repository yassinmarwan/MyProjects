package com.team22.eventticketing.user.dto;

public record AuthResponse(String token, long expiresIn) {}