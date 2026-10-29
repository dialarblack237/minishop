package com.example.minishop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",                  // Vite dev server (Module 12.3)
            "https://dialarblack237.github.io"        // hosted frontend, user page
    );

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();

        cors.setAllowedOrigins(ALLOWED_ORIGINS);

        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        cors.setAllowedHeaders(List.of("Authorization", "Idempotency-Key", "Content-Type"));

        cors.setExposedHeaders(List.of("Location", "Idempotency-Replayed"));

        // MiniShop uses bearer tokens, not cookies; there is nothing to send with credentials.
        cors.setAllowCredentials(false);

        // Browser caches the preflight result for 10 minutes — fewer round-trips, same policy.
        cors.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}