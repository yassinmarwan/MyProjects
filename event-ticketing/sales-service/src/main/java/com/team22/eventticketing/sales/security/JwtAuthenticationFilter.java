package com.team22.eventticketing.sales.security;

import com.team22.eventticketing.sales.service.JwtService;
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

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.endsWith("/health");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        UserLoaderHandler userLoaderHandler = new UserLoaderHandler();

        AuthHandler tokenExtraction = new TokenExtractionHandler();
        AuthHandler signatureValidation = new SignatureValidationHandler(jwtService);
        AuthHandler roleAuthorization = new RoleAuthorizationHandler();

        tokenExtraction.setNext(signatureValidation);
        signatureValidation.setNext(userLoaderHandler);
        userLoaderHandler.setNext(roleAuthorization);

        AuthContext ctx = new AuthContext(request, "ATTENDEE");

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

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}