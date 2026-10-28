package com.example.minishop.security;

import com.example.minishop.domain.Customer;
import com.example.minishop.repository.CustomerRepository;
import com.example.minishop.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminEndpointSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired CustomerRepository customerRepo;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        Customer admin = customerRepo.findByEmail("admin@minishop.local").orElseThrow();
        Customer customer = customerRepo.findByEmail("sam@minishop.local").orElseGet(() -> {
            Customer c = new Customer(
                    "sam@minishop.local",
                    "Sam",
                    "$2a$10$abcdefghijklmnopqrstuv");
            c.setRole("CUSTOMER");
            return customerRepo.save(c);
        });
        adminToken    = jwtService.sign(admin,    Duration.ofMinutes(15));
        customerToken = jwtService.sign(customer, Duration.ofMinutes(15));
    }

    // ── DLQ replay: no token → 403 today (401-vs-403 gap deferred; see known-gaps #3) ──
    @Test
    void dlqReplay_noToken_is403() throws Exception {
        mvc.perform(post("/admin/dlq/" + 1L + "/replay"))
           .andExpect(status().isForbidden());
    }

    // ── DLQ replay: customer token → 403 ───────────────────────────────
    @Test
    void dlqReplay_asCustomer_is403() throws Exception {
        mvc.perform(post("/admin/dlq/" + 1L + "/replay")
                .header("Authorization", "Bearer " + customerToken))
           .andExpect(status().isForbidden());
    }

    // ── DLQ replay: admin token → reaches handler (404 for unknown id) ─
    @Test
    void dlqReplay_asAdmin_reachesHandler_is404() throws Exception {
        mvc.perform(post("/admin/dlq/" + 999_999L + "/replay")
                .header("Authorization", "Bearer " + adminToken))
           .andExpect(status().isNotFound());
    }

    // ── Product write: customer token → 403 (controller @PreAuthorize) ─
    @Test
    void productCreate_asCustomer_is403() throws Exception {
        mvc.perform(post("/products")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"priceCents\":1,\"stock\":1}"))
           .andExpect(status().isForbidden());
    }

    // ── Product write: admin token → 201 ───────────────────────────────
    @Test
    void productCreate_asAdmin_is201() throws Exception {
        mvc.perform(post("/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Y-" + UUID.randomUUID() + "\",\"priceCents\":1,\"stock\":1}"))
           .andExpect(status().isCreated());
    }
}