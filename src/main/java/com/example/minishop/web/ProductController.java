package com.example.minishop.web;

import com.example.minishop.service.ProductService;
import com.example.minishop.web.dto.ProductDto;
import com.example.minishop.web.dto.ProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Tag(name = "Products", description = "Read and (for ADMIN) manage products; reads are cached.")
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(summary = "List products, paginated",
            description = "Returns one page of products. Use ?page=&size=&sort=. Page size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "A page of products")
    @GetMapping
    public ResponseEntity<Page<ProductDto>> list(Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }
        return ResponseEntity.ok(service.list(pageable));
    }

    @Operation(summary = "Read one product by id",
            description = "Returns 200 with the product, or 404 if it does not exist.")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Operation(summary = "Create a product", description = "Returns 201 with a Location header. Requires ADMIN role.")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest req,
                                             UriComponentsBuilder uriBuilder) {
        ProductDto created = service.create(req);
        URI location = uriBuilder.path("/products/{id}")
                                 .buildAndExpand(created.id())
                                 .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Update a product",
            description = "Returns 204 No Content. Requires ADMIN role.")
    @ApiResponse(responseCode = "204", description = "Product updated")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable long id,
                                       @Valid @RequestBody ProductRequest req) {
        service.update(id, req);
        return ResponseEntity.noContent().build();
    }
}