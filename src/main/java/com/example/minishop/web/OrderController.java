package com.example.minishop.web;

import com.example.minishop.domain.Customer;
import com.example.minishop.repository.CustomerRepository;
import com.example.minishop.service.CustomerNotFound;
import com.example.minishop.service.OrderService;
import com.example.minishop.web.dto.OrderDto;
import com.example.minishop.web.dto.OrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Tag(name = "Orders", description = "Place orders; read own orders.")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CustomerRepository customerRepo;

    public OrderController(OrderService orderService, CustomerRepository customerRepo) {
        this.orderService = orderService;
        this.customerRepo = customerRepo;
    }

    @Operation(
            summary = "Place an order",
            description = "Validates the cart, snapshots prices, and returns 202 Accepted with the order id. " +
                          "Full pipeline (reservation, payment, confirmation) runs asynchronously.")
    @ApiResponse(responseCode = "202", description = "Order accepted for processing")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @ApiResponse(responseCode = "409", description = "Insufficient stock")
    @PostMapping
    public ResponseEntity<OrderDto> placeOrder(@Valid @RequestBody OrderRequest req,
                                               UriComponentsBuilder uriBuilder) {
        Customer customer = currentCustomer();
        OrderDto created = orderService.placeOrder(req, customer);
        URI location = uriBuilder.path("/orders/{id}")
                                 .buildAndExpand(created.id())
                                 .toUri();
        return ResponseEntity.accepted().location(location).body(created);
    }

    private Customer currentCustomer() {
        return customerRepo.findByEmail("admin@minishop.local")
                .orElseThrow(CustomerNotFound::new);
    }
}