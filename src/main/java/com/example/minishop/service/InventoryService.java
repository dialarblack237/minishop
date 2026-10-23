package com.example.minishop.service;

import com.example.minishop.domain.Product;
import com.example.minishop.repository.ProductRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ProductRepository productRepo;

    public InventoryService(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    @Transactional
    public void decrementStockPessimistic(long productId, int qty) {
        Product p = productRepo.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found: " + productId));
        if (p.getStock() < qty) {
            throw new IllegalStateException("Out of stock: " + productId);
        }
        p.setStock(p.getStock() - qty);
        productRepo.save(p);
    }

    @Transactional
    public void decrementStockOptimistic(long productId, int qty) {
        Product p = productRepo.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found: " + productId));
        if (p.getStock() < qty) {
            throw new IllegalStateException("Out of stock: " + productId);
        }
        p.setStock(p.getStock() - qty);
        try {
            productRepo.saveAndFlush(p);
        } catch (OptimisticLockingFailureException e) {
            throw new IllegalStateException("Stock conflict on product " + productId, e);
        }
    }
}