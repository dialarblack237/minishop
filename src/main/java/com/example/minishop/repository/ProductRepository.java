package com.example.minishop.repository;

import com.example.minishop.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStockGreaterThan(int min);

    Page<Product> findByNameContainingIgnoreCase(String fragment, Pageable pageable);

    List<Product> findTop10ByOrderByCreatedAtDesc();

    @Query("""
        SELECT p FROM Product p
        WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (:minPriceCents IS NULL OR p.priceCents >= :minPriceCents)
          AND (:inStock IS NULL OR p.stock > 0)
        """)
    Page<Product> search(@Param("name") String name,
                         @Param("minPriceCents") Integer minPriceCents,
                         @Param("inStock") Boolean inStock,
                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") long id);
}