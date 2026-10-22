package com.example.minishop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;   // <-- add this import

@SpringBootTest
@ActiveProfiles("dev")   // <-- add this line: test context uses dev profile
class MiniShopApplicationTests {

    @Test
    void contextLoads() {
    }

}