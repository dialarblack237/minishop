package com.example.minishop.repository;

import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    List<OrderHistory> findByOrderIdOrderByOccurredAtAsc(Long orderId);

    boolean existsByOrderIdAndToState(Long orderId, OrderState toState);
}