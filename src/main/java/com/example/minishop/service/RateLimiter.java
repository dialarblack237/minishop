package com.example.minishop.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        this.script.setResultType(List.class);
    }

    public RateLimitResult tryAcquire(String callerKey, int capacityPerMinute) {
        double ratePerSecond = capacityPerMinute / 60.0;
        List<?> result = redis.execute(
                script,
                List.of("bucket:" + callerKey),
                String.valueOf(capacityPerMinute),
                String.valueOf(ratePerSecond),
                String.valueOf(Instant.now().getEpochSecond())
        );
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Unexpected Lua result: " + result);
        }
        long allowed = ((Number) result.get(0)).longValue();
        long retry = ((Number) result.get(1)).longValue();
        return allowed == 1 ? RateLimitResult.allow() : RateLimitResult.refuse(retry);
    }
}