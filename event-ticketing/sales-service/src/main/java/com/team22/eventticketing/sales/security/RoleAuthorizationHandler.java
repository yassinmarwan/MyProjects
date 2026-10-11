package com.team22.eventticketing.sales.security;

import org.springframework.security.access.AccessDeniedException;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public void handle(AuthContext ctx) {
        String required = ctx.getRequiredRole();
        String actual = ctx.getUserRole();

        if (required == null || required.equals("ATTENDEE")) {
            passToNext(ctx);
            return;
        }

        if ("ADMIN".equals(required) && !"ADMIN".equals(actual)) {
            throw new AccessDeniedException("Insufficient role");
        }

        passToNext(ctx);
    }
}