package com.example.minishop.service;

import com.example.minishop.domain.Product;
import com.example.minishop.repository.OutboxRepository;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.repository.ReservationRepository;
import com.example.minishop.event.OrderCreated;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Practical-exam counterpart to 10.1.2's written exam.
 *
 * @SpringBootTest starts the whole app (the InventoryConsumer from 6.3.1 included).
 * @EmbeddedKafka starts a real Kafka broker inside the test JVM and wires
 * spring.kafka.bootstrap-servers to it, so the test produces and consumes
 * against a real broker — no mocks, no local Kafka on 9092.
 *
 * One test class, one test. Broker lifecycle is per-class; running two tests
 * here would leak messages between them (the page's "if you get stuck" box).
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"orders", "orders.DLQ", "orders.retry.5s", "orders.retry.30s", "orders.retry.5m"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ActiveProfiles("dev")
class InventoryConsumerIT {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ProductRepository productRepo;
    @Autowired ReservationRepository reservationRepo;
    @Autowired OutboxRepository outboxRepo;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        // Fresh state per test — the consumer decrements this product, so the
        // starting stock must be known and unique to this test class's rows.
        // Save by name so the product is unambiguous even if another test seeded
        // a different product earlier.
        outboxRepo.deleteAll();
        reservationRepo.deleteAll();
        productRepo.deleteAll();

        Product coffee = new Product("Coffee-IT", 1200, 5);
        productRepo.save(coffee);
        // assign a known id via reflection (the entity is @GeneratedValue) so the
        // event's product id matches what findByIdForUpdate will look up.
        try {
            var idField = Product.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(coffee, 42L);
            productRepo.save(coffee);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot assign product id", e);
        }
    }

    @Test
    @DisplayName("an OrderCreated event results in a stock decrement, a reservation row, and an InventoryReserved outbox row")
    void orderCreated_reservesStock_andWritesInventoryReserved() throws Exception {
        var event = new OrderCreated(
                UUID.randomUUID(),
                100L,
                7L,
                2400,
                List.of(new OrderCreated.Item(42L, 2, 1200)));
        String payload = objectMapper.writeValueAsString(event);
        kafka.send("orders", event.eventId().toString(), payload).get();

        // Awaitility, not Thread.sleep: poll until the consumer's side effects appear.
        // 10-second ceiling — generous for an embedded broker but still a hard bound.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(productRepo.findById(42L).orElseThrow().getStock())
                    .as("stock after reserving 2 of 5")
                    .isEqualTo(3);

            assertThat(reservationRepo.findAll())
                    .as("one reservation row for product 42, qty 2")
                    .hasSize(1);

            assertThat(outboxRepo.findAll())
                    .as("one outbox row for the InventoryReserved event")
                    .hasSize(1)
                    .allSatisfy(row -> {
                        assertThat(row.getTopic()).isEqualTo("orders");
                        assertThat(row.getPayload()).contains("\"orderId\":100");
                    });
        });
    }
}