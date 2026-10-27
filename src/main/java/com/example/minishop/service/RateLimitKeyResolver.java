package com.example.minishop.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class RateLimitKeyResolver {

    public String resolve(HttpServletRequest req, Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        String ip = req.getRemoteAddr();
        return "ip:" + (ip == null || ip.isBlank() ? "unknown" : ip);
    }
}