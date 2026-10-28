package com.example.minishop.service;

import com.example.minishop.domain.Product;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.web.dto.ProductDto;
import com.example.minishop.web.dto.ProductRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepo;

    public ProductService(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    @Cacheable(cacheNames = "product", key = "#id")
    public ProductDto get(long id) {
        return productRepo.findById(id)
                .map(ProductDto::from)
                .orElseThrow(() -> new ProductNotFound(id));
    }

    @Cacheable(cacheNames = "product:list",
               key = "#pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    public Page<ProductDto> list(Pageable pageable) {
        return productRepo.findAll(pageable).map(ProductDto::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
        @CacheEvict(cacheNames = "product", key = "#id"),
        @CacheEvict(cacheNames = "product:list", allEntries = true)
    })
    @Transactional
    public ProductDto update(long id, ProductRequest req) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new ProductNotFound(id));
        p.setName(req.name());
        p.setPriceCents(req.priceCents());
        p.setStock(req.stock());
        return ProductDto.from(productRepo.save(p));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = "product:list", allEntries = true)
    @Transactional
    public ProductDto create(ProductRequest req) {
        Product p = new Product(req.name(), req.priceCents(), req.stock());
        return ProductDto.from(productRepo.save(p));
    }
}