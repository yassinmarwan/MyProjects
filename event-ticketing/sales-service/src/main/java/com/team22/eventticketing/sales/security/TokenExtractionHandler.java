package com.team22.eventticketing.sales.security;

import org.springframework.security.authentication.BadCredentialsException;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    public void handle(AuthContext ctx) {
        String authHeader = ctx.getRequest().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing or malformed Authorization header");
        }

        ctx.setToken(authHeader.substring(7));
        passToNext(ctx);
    }
}