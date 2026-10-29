package com.example.minishop.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProductSortWhitelistTest {

    @Autowired MockMvc mvc;

    // ── Allowed sort field → 200 (behaviour unchanged for legitimate callers) ──
    @Test
    void listSortByPriceCents_is200() throws Exception {
        mvc.perform(get("/products").param("sort", "priceCents,asc"))
           .andExpect(status().isOk());
    }

    // ── Unknown sort field → 400 (ValidationException from ProductService.list) ──
    @Test
    void listSortByUnknownField_is400() throws Exception {
        mvc.perform(get("/products").param("sort", "evilColumn"))
           .andExpect(status().isBadRequest());
    }

    // ── Injection-shaped sort → 400 (the actual threat this page closes) ──
    @Test
    void listSortByInjectionShapedField_is400() throws Exception {
        mvc.perform(get("/products").param("sort", "(select 1)"))
           .andExpect(status().isBadRequest());
    }
}