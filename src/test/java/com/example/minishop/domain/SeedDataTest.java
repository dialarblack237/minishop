package com.example.minishop.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@ActiveProfiles("dev")
class SeedDataTest {

    @Autowired
    private EntityManager em;

    @Test
    void seed_migration_inserted_three_products_and_one_admin() {
        // The seed inserts three products with ids 1, 2, 3.
        Long productCount = (Long) em.createQuery(
                "SELECT COUNT(p) FROM Product p").getSingleResult();
        assertThat(productCount).isEqualTo(3L);

        // And exactly one admin.
        Long adminCount = (Long) em.createQuery(
                "SELECT COUNT(c) FROM Customer c WHERE c.role = 'ADMIN'")
                .getSingleResult();
        assertThat(adminCount).isEqualTo(1L);
    }
}