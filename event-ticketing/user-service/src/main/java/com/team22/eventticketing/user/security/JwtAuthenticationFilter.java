
package com.team22.eventticketing.user.security;

import com.team22.eventticketing.user.repository.UserRepository;
import com.team22.eventticketing.user.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") ||
                path.equals("/api/health") ||
                path.equals("/api/users/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requiredRole = resolveRequiredRole(request);

        UserLoaderHandler userLoaderHandler = new UserLoaderHandler(userRepository);

        AuthHandler tokenExtraction = new TokenExtractionHandler();
        AuthHandler signatureValidation = new SignatureValidationHandler(jwtService);
        AuthHandler roleAuthorization = new RoleAuthorizationHandler();

        tokenExtraction.setNext(signatureValidation);
        signatureValidation.setNext(userLoaderHandler);
        userLoaderHandler.setNext(roleAuthorization);

        AuthContext ctx = new AuthContext(request, requiredRole);

        try {
            tokenExtraction.handle(ctx);
        } catch (AccessDeniedException e) {
            writeError(response, 403, e.getMessage());
            return;
        } catch (AuthenticationException e) {
            writeError(response, 401, e.getMessage());
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                ctx.getUserEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getUserRole()))
        );
        authentication.setDetails(ctx.getUserId());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String rawAuth = request.getHeader("Authorization");
        AuthTokenHolder.set(rawAuth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthTokenHolder.clear();
        }
    }

    private String resolveRequiredRole(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("PUT".equalsIgnoreCase(method) && path.matches("/api/users/\\d+/role")) {
            return "ADMIN";
        }

        return "ATTENDEE";
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}