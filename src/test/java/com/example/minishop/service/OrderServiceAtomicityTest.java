package com.example.minishop.service;

import com.example.minishop.domain.Customer;
import com.example.minishop.repository.CustomerRepository;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.OutboxRepository;
import com.example.minishop.web.dto.OrderRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("Kafka consumers start during @SpringBootTest and consume real messages, " +
          "drifting DB counts. Replace with @EmbeddedKafka in Module 10.2.")
@SpringBootTest
@ActiveProfiles("dev")
class OrderServiceAtomicityTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private OrderHistoryRepository historyRepo;

    @Autowired
    private OutboxRepository outboxRepo;

    @Test
    void successful_placeOrder_writes_four_rows_atomically() {
        Customer customer = customerRepo.findByEmail("admin@minishop.local").orElseThrow();
        long ordersBefore = orderRepo.count();
        long historyBefore = historyRepo.count();
        long outboxBefore = outboxRepo.count();

        OrderRequest req = new OrderRequest(List.of(
                new OrderRequest.OrderLine(1L, 1),
                new OrderRequest.OrderLine(2L, 2)
        ));
        orderService.placeOrder(req, customer);

        assertThat(orderRepo.count()).isEqualTo(ordersBefore + 1);
        assertThat(historyRepo.count()).isEqualTo(historyBefore + 1);
        assertThat(outboxRepo.count()).isEqualTo(outboxBefore + 1);
    }

    @Test
    void failing_placeOrder_writes_nothing() {
        Customer customer = customerRepo.findByEmail("admin@minishop.local").orElseThrow();
        long ordersBefore = orderRepo.count();
        long historyBefore = historyRepo.count();
        long outboxBefore = outboxRepo.count();

        OrderRequest missingProduct = new OrderRequest(List.of(
                new OrderRequest.OrderLine(999_999L, 1)
        ));

        assertThatThrownBy(() -> orderService.placeOrder(missingProduct, customer))
                .isInstanceOf(ProductNotFound.class);

        assertThat(orderRepo.count()).isEqualTo(ordersBefore);
        assertThat(historyRepo.count()).isEqualTo(historyBefore);
        assertThat(outboxRepo.count()).isEqualTo(outboxBefore);
    }
}