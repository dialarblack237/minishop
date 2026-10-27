package com.example.minishop.service;

public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult allow() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult refuse(long retryAfterSeconds) {
        return new RateLimitResult(false, retryAfterSeconds);
    }
}