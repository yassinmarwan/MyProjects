package com.team22.eventticketing.sales.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

public class AuthContext {

    private final HttpServletRequest request;
    private String token;
    private Claims claims;
    private String userEmail;
    private String userRole;
    private Long userId;
    private String requiredRole;

    public AuthContext(HttpServletRequest request, String requiredRole) {
        this.request = request;
        this.requiredRole = requiredRole;
    }

    public HttpServletRequest getRequest() { return request; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Claims getClaims() { return claims; }
    public void setClaims(Claims claims) { this.claims = claims; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRequiredRole() { return requiredRole; }
    public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }
}