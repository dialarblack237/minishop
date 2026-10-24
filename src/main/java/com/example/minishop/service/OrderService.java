package com.example.minishop.service;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderItem;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.Product;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.web.dto.OrderDto;
import com.example.minishop.web.dto.OrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;

    public OrderService(OrderRepository orderRepo, ProductRepository productRepo) {
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
    }

    @Transactional
    public OrderDto placeOrder(OrderRequest req, com.example.minishop.domain.Customer customer) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("cart must not be empty");
        }

        Order order = new Order(customer);
        order.setState(OrderState.CREATED);

        int total = 0;
        for (OrderRequest.OrderLine line : req.items()) {
            Product product = productRepo.findById(line.productId())
                    .orElseThrow(() -> new ProductNotFound(line.productId()));

            if (product.getStock() < line.quantity()) {
                throw new StockUnavailable(product.getId());
            }

            OrderItem item = new OrderItem(product, product.getPriceCents(), line.quantity());
            order.addItem(item);
            total += product.getPriceCents() * line.quantity();
        }
        order.setTotalCents(total);

        return OrderDto.from(orderRepo.save(order));
    }
}