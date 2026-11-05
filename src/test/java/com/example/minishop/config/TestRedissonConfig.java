package com.example.minishop.config;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only overrides for two beans that would otherwise require a live Redis:
 *
 * 1. RedissonClient — OutboxPublisher needs one in its constructor. Mock is
 *    sufficient: only getLock() and tryLock() are called on the publisher path.
 *
 * 2. CacheManager — RedisCache goes through RedissonConnectionFactory, which
 *    casts its RedissonClient to org.redisson.Redisson unconditionally in
 *    RedissonConnection's constructor. A Mockito mock cannot satisfy that cast
 *    (ClassCastException). Replacing the CacheManager with an in-memory one
 *    sidesteps the cast entirely: no @Cacheable or @CacheEvict call opens a
 *    socket.
 *
 *    The bean is named "testCacheManager" (not "cacheManager") so it does NOT
 *    clash with CacheConfig.cacheManager — Spring Boot 3 disables bean override.
 *    @Primary wins the CacheManager injection by type.
 */
@TestConfiguration
public class TestRedissonConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        Config mockConfig = Mockito.mock(Config.class);
        Mockito.when(mockConfig.isClusterConfig()).thenReturn(false);

        RedissonClient mock = Mockito.mock(RedissonClient.class);
        RLock mockLock = Mockito.mock(RLock.class);
        try {
            Mockito.when(mockLock.tryLock(
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.any()))
                .thenReturn(true);
        } catch (InterruptedException ignored) {
            // Checked exception is unreachable on a stub; try/catch satisfies javac.
        }
        Mockito.when(mock.getLock(ArgumentMatchers.anyString())).thenReturn(mockLock);
        Mockito.when(mock.getConfig()).thenReturn(mockConfig);
        return mock;
    }

    @Bean("testCacheManager")
    @Primary
    public CacheManager testCacheManager() {
        // In-memory: @Cacheable / @CacheEvict work identically to the caller,
        // but no network is involved. Safe in CI (no Memurai) and locally (no
        // pollution of DB 0 or 15).
        return new ConcurrentMapCacheManager();
    }
}