package com.example.minishop.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    // We read the REDIS_URL environment variable directly.
    @Value("${REDIS_URL}")
    private String redisUrl;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // REDIS_URL in Render MUST start with rediss:// for TLS.
        // We pass it verbatim. Do not prepend anything.
        config.useSingleServer().setAddress(redisUrl);
        return Redisson.create(config);
    }
}