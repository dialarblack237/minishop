package com.example.minishop.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 10.2.3 — MockMvc + JSON assertions on three endpoints, one per layer test.
 *
 * @SpringBootTest boots the whole app; @AutoConfigureMockMvc brings the HTTP
 * layer in-process (no real Tomcat port). The real security chain is active —
 * authentication is supplied by @WithMockUser (the page's own "if you get
 * stuck" recommendation), NOT by disabling security.
 *
 * Uses @ActiveProfiles("test") so the test does not touch the developer's
 * dev H2 (see application-test.yml from 10.2.2; its Redis URL is on DB 15,
 * which this test does not use because it does not exercise the cache).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractIT {

    @Autowired MockMvc mvc;

    // ── TEST 1 — READ: 200 with JSON body. ────────────────
    // Uses the real seeded product id 1 (R__seed_data.sql), not the page's illustrative 42.
    @Test
    @WithMockUser(username = "sam@minishop.local", roles = "CUSTOMER")
    @DisplayName("GET /products/1 → 200 with the seeded product's shape")
    void getProduct_returns200_withJsonBody() throws Exception {
        mvc.perform(get("/products/1"))
           .andExpect(status().isOk())                                        // 1. status
           .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))  // 2. header
           .andExpect(jsonPath("$.id").value(1))                              // 3. body
           .andExpect(jsonPath("$.name").isString())
           .andExpect(jsonPath("$.priceCents").isNumber())
           .andExpect(jsonPath("$.stock").isNumber());
    }

    // ── TEST 2 — CREATE: 202 with Location header and body. ───
    @Test
    @WithMockUser(username = "sam@minishop.local", roles = "CUSTOMER")
    @DisplayName("POST /orders → 202 with Location header and state CREATED")
    void createOrder_returns202_withLocationHeader() throws Exception {
        String body = """
                {"items":[{"productId":1,"quantity":1}]}""";

        mvc.perform(post("/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(APPLICATION_JSON)
                .content(body))
           .andExpect(status().isAccepted())
           .andExpect(header().string("Location", matchesPattern("/orders/\\d+")))
           .andExpect(jsonPath("$.id").isNumber())
           .andExpect(jsonPath("$.state").value("CREATED"));
    }

    // ── TEST 3 — VALIDATION: 400 with per-field errors. ───
    // Empty items array → @NotEmpty on OrderRequest.items → 400.
    @Test
    @WithMockUser(username = "sam@minishop.local", roles = "CUSTOMER")
    @DisplayName("POST /orders with empty items → 400 with field errors in the problem detail")
    void createOrder_emptyCart_returns400_withFieldErrors() throws Exception {
        String body = """
                {"items":[]}""";

        mvc.perform(post("/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(APPLICATION_JSON)
                .content(body))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.title").value("Validation failed"))
           .andExpect(jsonPath("$.errors").isArray())
           .andExpect(jsonPath("$.errors[0].field").exists())
           .andExpect(jsonPath("$.errors[0].message").isString())
           .andExpect(jsonPath("$.traceId").exists());
    }
}