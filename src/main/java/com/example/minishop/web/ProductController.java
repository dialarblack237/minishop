package com.example.minishop.web;

import com.example.minishop.service.ProductService;
import com.example.minishop.web.dto.ProductDto;
import com.example.minishop.web.dto.ProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<ProductDto>> list(Pageable pageable) {
        return ResponseEntity.ok(service.list(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@RequestBody ProductRequest req,
                                             UriComponentsBuilder uriBuilder) {
        ProductDto created = service.create(req);
        URI location = uriBuilder.path("/products/{id}")
                                 .buildAndExpand(created.id())
                                 .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable long id,
                                       @RequestBody ProductRequest req) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }
}