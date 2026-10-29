package com.example.minishop.service;

import com.example.minishop.domain.Customer;
import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.Product;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.OutboxRepository;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.web.dto.OrderDto;
import com.example.minishop.web.dto.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderService.placeOrder(OrderRequest, Customer)")
class OrderServiceUnitTest {

    private FakeProductRepository productRepo;
    private StubOrderRepository orderRepo;
    private StubOrderHistoryRepository historyRepo;
    private StubOutboxRepository outboxRepo;

    private OrderService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        productRepo = new FakeProductRepository();

        // Seed the product, then FORCE its id to 42 — the id the request uses.
        // Moving the map entry would leave the entity's own getId() reporting the old value,
        // which is why StockUnavailable reported "1000" instead of "42".
        Product coffee = new Product("Coffee", 1200, 5);
        assignId(coffee, Product.class, 42L);
        productRepo.store.put(42L, coffee);

        orderRepo = new StubOrderRepository();
        historyRepo = new StubOrderHistoryRepository();
        outboxRepo = new StubOutboxRepository();

        service = new OrderService(
                orderRepo,
                historyRepo,
                outboxRepo,
                productRepo,
                new NoopIdemService(new ObjectMapper()),
                new ObjectMapper());

        customer = new Customer("tester@minishop.local", "Tester", "$2a$10$placeholder");
        customer.setRole("CUSTOMER");
        assignId(customer, Customer.class, 1L);
    }

    // ── TEST 1 — happy path. ──────────────────────────────
    @Test
    @DisplayName("creates an order with state CREATED and the right total")
    void placesOrder_withSufficientStock() {
        OrderRequest req = new OrderRequest(List.of(
                new OrderRequest.OrderLine(42L, 2)));

        OrderDto dto = service.placeOrder(req, customer);

        assertThat(dto.state()).isEqualTo(OrderState.CREATED);
        assertThat(dto.totalCents()).isEqualTo(2400);
        assertThat(dto.items()).hasSize(1);
        assertThat(outboxRepo.saved).hasSize(1);
    }

    // ── TEST 2 — the business rule refuses. ───────────────
    @Test
    @DisplayName("rejects the order when stock is insufficient")
    void rejectsOrder_whenStockInsufficient() {
        OrderRequest req = new OrderRequest(List.of(
                new OrderRequest.OrderLine(42L, 99)));

        assertThatThrownBy(() -> service.placeOrder(req, customer))
                .isInstanceOf(StockUnavailable.class)
                .hasMessageContaining("42");
        assertThat(orderRepo.saved).isEmpty();
        assertThat(outboxRepo.saved).isEmpty();
    }

    // ── TEST 3 — the snapshot rule. ───────────────────────
    @Test
    @DisplayName("price is snapshotted from the product, not from the request")
    void snapshotsPriceFromProduct() {
        OrderRequest req = new OrderRequest(List.of(new OrderRequest.OrderLine(42L, 1)));

        OrderDto dto = service.placeOrder(req, customer);

        assertThat(dto.items())
                .singleElement()
                .extracting(OrderDto.Item::unitPriceCents)
                .isEqualTo(1200);

        productRepo.store.get(42L).setPriceCents(9999);
        assertThat(dto.items().get(0).unitPriceCents()).isEqualTo(1200);
    }

    // ──────────────────────── fakes ────────────────────────

    static class FakeProductRepository implements ProductRepository {
        final Map<Long, Product> store = new LinkedHashMap<>();
        private final AtomicLong nextId = new AtomicLong(1_000);

        @Override public Optional<Product> findById(Long id) { return Optional.ofNullable(store.get(id)); }

        @Override public <S extends Product> S save(S entity) {
            assignIdIfNull(entity, Product.class, nextId);
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public List<Product> findByStockGreaterThan(int min) { throw new UnsupportedOperationException(); }
        @Override public Page<Product> findByNameContainingIgnoreCase(String fragment, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public List<Product> findTop10ByOrderByCreatedAtDesc() { throw new UnsupportedOperationException(); }
        @Override public Page<Product> search(String name, Integer minPriceCents, Boolean inStock, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public Optional<Product> findByIdForUpdate(long id) { throw new UnsupportedOperationException(); }
        @Override public List<Product> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Product> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<Product> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public List<Product> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(Product entity) { store.values().remove(entity); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Product> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends Product> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Product, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public void flush() { }
        @Override public <S extends Product> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Product> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<Product> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { store.clear(); }
        @Override public Product getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public Product getById(Long id) { return store.get(id); }
        @Override public Product getReferenceById(Long id) { return store.get(id); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
    }

    static class StubOrderRepository implements OrderRepository {
        final List<Order> saved = new ArrayList<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override public <S extends Order> S save(S entity) {
            assignIdIfNull(entity, Order.class, nextId);
            saved.add(entity);
            return entity;
        }

        @Override public List<Order> findByCustomerIdAndState(Long customerId, OrderState state) { throw new UnsupportedOperationException(); }
        @Override public Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public Optional<Order> findByIdAndCustomerId(Long id, Long customerId) { throw new UnsupportedOperationException(); }
        @Override public Optional<Order> findByIdForUpdate(long id) { throw new UnsupportedOperationException(); }

        @Override public <S extends Order> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Order> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<Order> findAll() { throw new UnsupportedOperationException(); }
        @Override public List<Order> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { throw new UnsupportedOperationException(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(Order entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Order> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public List<Order> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<Order> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public void flush() { }
        @Override public <S extends Order> S saveAndFlush(S entity) { throw new UnsupportedOperationException(); }
        @Override public <S extends Order> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<Order> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public Order getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public Order getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public Order getReferenceById(Long id) { throw new UnsupportedOperationException(); }
    }

    static class StubOrderHistoryRepository implements OrderHistoryRepository {
        final List<OrderHistory> saved = new ArrayList<>();

        @Override public <S extends OrderHistory> S save(S entity) { saved.add(entity); return entity; }

        @Override public List<OrderHistory> findByOrderIdOrderByOccurredAtAsc(Long orderId) { throw new UnsupportedOperationException(); }
        @Override public boolean existsByOrderIdAndToState(Long orderId, OrderState toState) { throw new UnsupportedOperationException(); }
        @Override public List<OrderHistory> findJourney(long orderId) { throw new UnsupportedOperationException(); }

        @Override public <S extends OrderHistory> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<OrderHistory> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<OrderHistory> findAll() { throw new UnsupportedOperationException(); }
        @Override public List<OrderHistory> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { throw new UnsupportedOperationException(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(OrderHistory entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends OrderHistory> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public List<OrderHistory> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<OrderHistory> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public void flush() { }
        @Override public <S extends OrderHistory> S saveAndFlush(S entity) { throw new UnsupportedOperationException(); }
        @Override public <S extends OrderHistory> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<OrderHistory> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public OrderHistory getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public OrderHistory getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public OrderHistory getReferenceById(Long id) { throw new UnsupportedOperationException(); }
    }

    static class StubOutboxRepository implements OutboxRepository {
        final List<OutboxRow> saved = new ArrayList<>();

        @Override public <S extends OutboxRow> S save(S entity) { saved.add(entity); return entity; }

        @Override public List<OutboxRow> findUnsent(Pageable pageable) { throw new UnsupportedOperationException(); }

        @Override public <S extends OutboxRow> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<OutboxRow> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { throw new UnsupportedOperationException(); }
        @Override public List<OutboxRow> findAll() { throw new UnsupportedOperationException(); }
        @Override public List<OutboxRow> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public long count() { throw new UnsupportedOperationException(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(OutboxRow entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends OutboxRow> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public List<OutboxRow> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<OutboxRow> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public void flush() { }
        @Override public <S extends OutboxRow> S saveAndFlush(S entity) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxRow> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<OutboxRow> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public OutboxRow getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public OutboxRow getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public OutboxRow getReferenceById(Long id) { throw new UnsupportedOperationException(); }
    }

    static class NoopIdemService extends IdemService {
        NoopIdemService(ObjectMapper objectMapper) { super(null, objectMapper); }
    }

    private static void assignIdIfNull(Object entity, Class<?> cls, AtomicLong nextId) {
        try {
            Field idField = cls.getDeclaredField("id");
            idField.setAccessible(true);
            if (idField.get(entity) == null) idField.set(entity, nextId.getAndIncrement());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("fake cannot assign id to " + cls.getSimpleName(), e);
        }
    }

    private static void assignId(Object entity, Class<?> cls, long value) {
        try {
            Field idField = cls.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot assign id to " + cls.getSimpleName(), e);
        }
    }
}