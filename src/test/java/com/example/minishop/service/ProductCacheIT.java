package com.example.minishop.service;

import com.example.minishop.domain.Product;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.web.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

/**
 * 10.2.2 — cache integration test.
 *
 * Real Redis (Memurai on localhost:6379) with @ActiveProfiles("test") pointing at DB 15.
 * Real H2 (test profile, separate in-memory DB name from dev's, so no collision).
 * Real ProductService (with @Cacheable). One @SpyBean on ProductRepository so that
 * "DB unreachable" between reads is a real, provable state — not a mock-shaped assumption.
 *
 * One test class, one test. Cache flush per test via @BeforeEach on the TEST DB (15).
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductCacheIT {

    @Autowired ProductService service;
    @Autowired StringRedisTemplate redis;
    @Autowired ProductRepository productRepo;

    // Spy on the real repo so we can make "DB down" real for the second read.
    @SpyBean ProductRepository spiedProductRepo;

    @BeforeEach
    void resetCache() {
        // FLUSHDB on DB 15 (test DB). With application-test.yml pointing at /15,
        // this cannot touch the developer's dev cache on DB 0.
        // Spring's cache key for @Cacheable(cacheNames = "product", key = "#id")
        // is "product::42" — two colons, RedisCacheManager's default separator.
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();

        // Seed the product directly through the JPA repo (bypasses cache).
        // Product has no id setter — reflection to give it a known id, matching 10.1.1/10.1.2.
        productRepo.deleteAll();
        Product coffee = new Product("Coffee-CacheIT", 1200, 5);
        try {
            var idField = Product.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(coffee, 42L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot assign product id", e);
        }
        productRepo.save(coffee);
    }

    @Test
    @DisplayName("first read misses, second read is served from Redis (repo unreachable between reads)")
    void secondRead_isServedFromCache() {
        // 1. First read — MISS: hits DB, populates cache.
        ProductDto first = service.get(42L);
        assertThat(first.id()).isEqualTo(42L);

        // The cache wrote a key with Spring's default separator: product::42.
        // The page's snippet says product:42 (one colon) — that is a raw-template key;
        // @Cacheable through RedisCacheManager writes product::42 (two colons).
        assertThat(redis.hasKey("product::42"))
                .as("cache key written by @Cacheable through RedisCacheManager")
                .isTrue();

        // 2. Make the DB unreachable — for the second read, any DB call throws.
        doThrow(new IllegalStateException("db down"))
                .when(spiedProductRepo).findById(42L);

        // 3. Second read — HIT: must succeed because the cache serves it; if it went
        //    to the DB, the spy throws and the test fails loudly.
        ProductDto second = service.get(42L);

        assertThat(second).isEqualTo(first);

        // 4. Prove the "unreachable" was real by calling the repo directly:
        //    the spy is actually throwing, so the assertion above succeeded only
        //    because the second read was served from the cache.
        assertThatThrownBy(() -> spiedProductRepo.findById(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}