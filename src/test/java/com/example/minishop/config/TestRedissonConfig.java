package com.example.minishop.config;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Test-only infrastructure overrides.
 *
 * Two problems this file solves, both discovered the hard way:
 *
 * 1. OutboxPublisher's constructor needs a RedissonClient. Without one, the
 *    context fails. We mock it (only getLock() is ever called on the publisher
 *    path), and stub Config so a stray isClusterConfig() doesn't NPE.
 *
 * 2. Spring Cache's RedisCache goes through RedissonConnectionFactory, which
 *    does an unconditional cast of RedissonClient to org.redisson.Redisson
 *    inside RedissonConnection's constructor. A Mockito mock CANNOT satisfy
 *    that cast — we saw ClassCastException. The cure is not to mock deeper, it
 *    is to supply a different RedisConnectionFactory implementation entirely.
 *    LettuceConnectionFactory is Spring Data's plain, cast-free alternative. It
 *    points at the same Redis URL as application-test.yml (DB 15), so caching
 *    still actually works, just without Redisson in the path.
 *
 * Both beans are @Primary so they win against the auto-configured ones by type.
 * Names differ from CacheConfig's beans, so no BeanDefinitionOverrideException.
 */
@TestConfiguration
public class TestRedissonConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        Config mockConfig = Mockito.mock(Config.class);
        // The one field RedissonConnection.getConnection() reads before the cast;
        // answering it keeps some diagnostic paths quiet.
        Mockito.when(mockConfig.isClusterConfig()).thenReturn(false);

        RedissonClient mock = Mockito.mock(RedissonClient.class);
        RLock mockLock = Mockito.mock(RLock.class);
        try {
            // OutboxPublisher uses tryLock(wait, lease, unit). Returning true
            // keeps the publisher on its happy path; false only delays things.
            Mockito.when(mockLock.tryLock(
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.any()))
                .thenReturn(true);
        } catch (InterruptedException ignored) {
            // Checked exception is unreachable on a stub; the try/catch satisfies javac.
        }
        Mockito.when(mock.getLock(ArgumentMatchers.anyString())).thenReturn(mockLock);
        Mockito.when(mock.getConfig()).thenReturn(mockConfig);
        return mock;
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        // Points at the same DB as application-test.yml so caching still works.
        // Lettuce is a plain connection implementation with NO cast to a concrete
        // Redisson class — the exact thing RedissonConnectionFactory is missing.
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        // Use database index 15 to match application-test.yml, so tests do not
        // pollute the developer's local DB 0.
        factory.setDatabase(15);
        // Important: LettuceConnectionFactory must be initialized before first use
        // when not managed by Spring Boot's own lifecycle in this @TestConfiguration
        // context. afterPropertiesSet() triggers that init.
        factory.afterPropertiesSet();
        return factory;
    }
}