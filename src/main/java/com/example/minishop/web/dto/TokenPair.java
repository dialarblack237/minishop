package com.example.minishop.web.dto;

public record TokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInSeconds
) {
    public static TokenPair of(String access, String refresh, long accessSeconds) {
        return new TokenPair(access, refresh, "Bearer", accessSeconds);
    }
}