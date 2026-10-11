package com.team22.eventticketing.sales.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;

public class UserLoaderHandler extends AuthHandler {

    @Override
    public void handle(AuthContext ctx) {
        Claims claims = ctx.getClaims();

        String email = claims.getSubject();
        String role = claims.get("role", String.class);
        Long uid = claims.get("uid", Long.class);

        if (email == null || role == null || uid == null) {
            throw new BadCredentialsException("Token is missing required claims");
        }

        ctx.setUserEmail(email);
        ctx.setUserRole(role);
        ctx.setUserId(uid);

        passToNext(ctx);
    }
}