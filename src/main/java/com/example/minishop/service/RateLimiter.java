package com.example.minishop.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimiter {

    private static final int CAPACITY = 100;
    private static final double RATE_PER_SECOND = 100.0 / 60.0;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Object> script;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.script.setResultType(Object.class);
    }

    public RateLimitResult tryAcquire(String callerKey) {
        Object raw = redis.execute(
                script,
                List.of("bucket:" + callerKey),
                String.valueOf(CAPACITY),
                String.valueOf(RATE_PER_SECOND),
                String.valueOf(Instant.now().getEpochSecond())
        );
        if (!(raw instanceof List<?> result) || result.size() < 2) {
            throw new IllegalStateException("Unexpected Lua result: " + raw);
        }
        long allowed = ((Number) result.get(0)).longValue();
        long retry = ((Number) result.get(1)).longValue();
        return allowed == 1 ? RateLimitResult.allow() : RateLimitResult.refuse(retry);
    }
}