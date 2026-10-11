package com.team22.eventticketing.user.security;

public class AuthTokenHolder {

    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    public static void set(String authorizationHeader) { holder.set(authorizationHeader); }
    public static String get() { return holder.get(); }
    public static void clear() { holder.remove(); }
}
