package com.team22.eventticketing.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER     = "X-Correlation-ID";
    private static final String MDC_KEY    = "correlationId";
    static final String         AUTH_KEY   = "authorizationHeader";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // STEP 1 — read from header (set by gateway) or generate if missing
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        // STEP 2 — load MDC and pass the request onward
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        String auth = request.getHeader("Authorization");
        if (auth != null) MDC.put(AUTH_KEY, auth);

        try {
            chain.doFilter(request, response);
        } finally {
            // STEP 3 — clean up so it doesn't leak into unrelated requests
            MDC.remove(MDC_KEY);
            MDC.remove(AUTH_KEY);
        }
    }
}