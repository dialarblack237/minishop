package com.example.minishop.repository;

import com.example.minishop.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStockGreaterThan(int min);

    Page<Product> findByNameContainingIgnoreCase(String fragment, Pageable pageable);

    List<Product> findTop10ByOrderByCreatedAtDesc();
}