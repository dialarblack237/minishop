package com.example.minishop.repository;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerIdAndState(Long customerId, OrderState state);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);
}