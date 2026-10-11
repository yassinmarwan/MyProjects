package com.team22.eventticketing.sales.config;

import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import io.jsonwebtoken.io.Decoders;

public class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private final String secret;
    private final long expirationMs;
    private final SecretKey signingKey;

    private JwtConfigurationManager() {
        String envSecret = System.getenv("JWT_SECRET");
        String envExpiration = System.getenv("JWT_EXPIRATION_MS");

        this.secret = (envSecret != null && !envSecret.isBlank())
                ? envSecret
                : "24966c8a763b3a3f7f264cefb93d95159184e8c5e2af5c94e05387371d47a3fd=";

        this.expirationMs = (envExpiration != null && !envExpiration.isBlank())
                ? Long.parseLong(envExpiration)
                : 86400000;

        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.secret));
    }

    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public SecretKey getSigningKey() {
        return signingKey;
    }
}