package com.kalima.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * CORS allow-list (browser origins), via {@code kalima.cors.allowed-origins}
 * (comma-separated). In production, set {@code CORS_ALLOWED_ORIGINS}; with an
 * empty list, cross-origin browser calls with cookies/credentials are not used.
 */
@ConfigurationProperties(prefix = "kalima.cors")
public class KalimaCorsProperties {

    private String allowedOrigins =
            "http://localhost:3000,http://localhost:3001,http://localhost:3333";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> allowedOriginList() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public List<String> allowedOriginPatterns() {
        List<String> explicitOrigins = allowedOriginList();
        if (explicitOrigins.isEmpty()) {
            return List.of();
        }

        return explicitOrigins.stream()
                .filter(origin -> origin.contains("*") || origin.contains("?"))
                .toList();
    }
}
