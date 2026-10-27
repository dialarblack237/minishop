package com.example.minishop.service;

import org.springframework.stereotype.Component;

@Component
public class RateLimitPolicy {

    public record Limit(int perMinute, boolean failClosed) {}

    public Limit forRequest(String method, String path) {
        if ("POST".equals(method) && path.startsWith("/auth")) {
            return new Limit(5, true);
        }
        if ("POST".equals(method)) {
            return new Limit(10, true);
        }
        if ("GET".equals(method)) {
            return new Limit(100, false);
        }
        return new Limit(30, true);
    }
}