package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.config.JwtConfigurationManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtService {

    private final JwtConfigurationManager jwtConfig = JwtConfigurationManager.getInstance();

    public String generateToken(Long userId, String email, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtConfig.getExpirationMs()))
                .signWith(jwtConfig.getSigningKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return jwtConfig.getExpirationMs();
    }
}