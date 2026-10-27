package com.example.minishop.config;

import com.example.minishop.service.RateLimitKeyResolver;
import com.example.minishop.service.RateLimitPolicy;
import com.example.minishop.service.RateLimitResult;
import com.example.minishop.service.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicy policy;

    public RateLimitFilter(RateLimiter rateLimiter,
                           RateLimitKeyResolver keyResolver,
                           RateLimitPolicy policy) {
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String key = keyResolver.resolve(req, auth);
        RateLimitPolicy.Limit limit = policy.forRequest(req.getMethod(), req.getRequestURI());

        try {
            RateLimitResult result = rateLimiter.tryAcquire(key, limit.perMinute());
            if (!result.allowed()) {
                writeRateLimited(res, req, result, limit);
                return;
            }
        } catch (Exception redisDown) {
            log.warn("rate limiter unavailable, key={} failClosed={}", key, limit.failClosed(), redisDown);
            if (limit.failClosed()) {
                res.setStatus(503);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private void writeRateLimited(HttpServletResponse res,
                                  HttpServletRequest req,
                                  RateLimitResult result,
                                  RateLimitPolicy.Limit limit) throws IOException {
        long retryAfter = result.retryAfterSeconds();
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(retryAfter));
        res.setHeader("X-RateLimit-Limit", String.valueOf(limit.perMinute()));
        res.setHeader("X-RateLimit-Remaining", "0");
        res.setHeader("X-RateLimit-Reset", Instant.now().plusSeconds(retryAfter).toString());

        String traceId = MDC.get("traceId");
        String body = String.format(
                "{\"type\":\"https://minishop.example/errors/rate-limited\"," +
                "\"title\":\"Too many requests\"," +
                "\"status\":429," +
                "\"detail\":\"You have made too many requests. Please wait %d seconds before retrying.\"," +
                "\"instance\":\"%s\"," +
                "\"traceId\":\"%s\"}",
                retryAfter,
                req.getRequestURI(),
                traceId == null ? "" : traceId
        );

        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(body);
    }
}