package com.example.minishop.domain;

import com.example.minishop.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest boots only the JPA slice (H2, entities).
@DataJpaTest
// @Import brings JpaAuditingConfig in so @CreatedDate works.
@Import(JpaAuditingConfig.class)
@ActiveProfiles("dev")
class OptimisticLockingTest {

    @Autowired
    private EntityManagerFactory emf;

    @Test
    void two_writers_same_version_one_wins_one_is_refused() {
        // 1. Create a product once, using a fresh session.
        Long productId;
        EntityManager em0 = emf.createEntityManager();
        try {
            em0.getTransaction().begin();
            Product product = new Product("Test Coffee", 1000, 50);
            em0.persist(product);
            em0.getTransaction().commit();
            productId = product.getId();
        } finally {
            em0.close();
        }

        // 2. Admin A opens their own session and loads the row.
        EntityManager emA = emf.createEntityManager();
        emA.getTransaction().begin();
        Product adminA = emA.find(Product.class, productId);
        int aVersion = adminA.getVersion();

        // 3. Admin B opens a separate session and loads the same row.
        EntityManager emB = emf.createEntityManager();
        emB.getTransaction().begin();
        Product adminB = emB.find(Product.class, productId);
        int bVersion = adminB.getVersion();

        // Both saw the same version — that is the "10:00" state.
        assertThat(aVersion).isEqualTo(bVersion);

        // 4. Admin A saves first and commits. Version bumps.
        adminA.setPriceCents(1200);
        emA.getTransaction().commit();
        emA.close();

        adminB.setPriceCents(1100);
        assertThatThrownBy(() -> emB.getTransaction().commit())
            .isInstanceOf(RollbackException.class)
            .hasCauseInstanceOf(OptimisticLockException.class);

        if (emB.getTransaction().isActive()) {
            emB.getTransaction().rollback();
        }
        emB.close();
    }
}