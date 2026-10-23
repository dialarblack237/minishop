package com.example.minishop.repository;

import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    List<OrderHistory> findByOrderIdOrderByOccurredAtAsc(Long orderId);

    boolean existsByOrderIdAndToState(Long orderId, OrderState toState);

    @Query("""
        SELECT h FROM OrderHistory h
        JOIN FETCH h.order o
        WHERE o.id = :orderId
        ORDER BY h.occurredAt ASC
        """)
    List<OrderHistory> findJourney(@Param("orderId") long orderId);
}