package com.team22.eventticketing.sales.security;

public abstract class AuthHandler {

    protected AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    public abstract void handle(AuthContext ctx);

    protected void passToNext(AuthContext ctx) {
        if (next != null) {
            next.handle(ctx);
        }
    }
}