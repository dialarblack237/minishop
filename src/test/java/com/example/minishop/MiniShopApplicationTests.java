package com.example.minishop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.example.minishop.config.TestRedissonConfig;

@SpringBootTest
@org.springframework.context.annotation.Import(TestRedissonConfig.class)
@ActiveProfiles("test")
class MiniShopApplicationTests {

    @Test
    void contextLoads() {
    }

}