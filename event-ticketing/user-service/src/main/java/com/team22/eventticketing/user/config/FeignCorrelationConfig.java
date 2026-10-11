package com.team22.eventticketing.user.config;

import com.team22.eventticketing.user.security.AuthTokenHolder;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }

    @Bean
    public RequestInterceptor jwtForwardingInterceptor() {
        return template -> {
            String auth = AuthTokenHolder.get();
            if (auth != null) {
                template.header("Authorization", auth);
            }
        };
    }
}