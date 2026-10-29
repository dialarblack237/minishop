package com.example.minishop.service;

import com.example.minishop.domain.Product;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.web.dto.ProductDto;
import com.example.minishop.web.dto.ProductRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ProductService {

    // The ONLY sort fields a client may ask for. Anything else → ValidationException → 400.
    private static final Set<String> ALLOWED_SORTS = Set.of("name", "priceCents", "createdAt", "stock");

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
        // Validate every requested sort field, then rebuild the Pageable from trusted values only.
        // After this, no client-supplied string reaches the repository's ORDER BY clause.
        Sort safeSort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORTS.contains(order.getProperty())) {
                throw new ValidationException(
                        "unknown sort field: " + order.getProperty(),
                        java.util.List.of(java.util.Map.of("field", "sort",
                                "message", "allowed: " + ALLOWED_SORTS)));
            }
            safeSort = safeSort.and(Sort.by(order.getDirection(), order.getProperty()));
        }
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), safeSort);
        return productRepo.findAll(safePageable).map(ProductDto::from);
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