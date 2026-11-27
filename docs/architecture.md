# MiniShop architecture

## Request path
Client -> `TraceIdFilter` (adds an `X-Trace-Id`) -> `RateLimitFilter` (Redis token bucket) -> `JwtAuthenticationFilter` -> controller -> service -> repository -> PostgreSQL.

Controllers are thin. Business rules live in services; the order lifecycle lives in `OrderStateMachine`, which never touches the database and is tested with a table of allowed (state, event) pairs.

## Order lifecycle

```
CREATED --InventoryReserved--> RESERVED --PaymentSucceeded--> PAID --OrderConfirmed--> CONFIRMED --RefundRequested--> REFUNDED
   |                              |
   +--InventoryRejected--> CANCELLED <--PaymentFailed--+
```

`CANCELLED` and `REFUNDED` are terminal. Any other transition raises `IllegalStateTransition`.

## Placing an order (synchronous part)
1. `POST /orders` with an `Idempotency-Key` header. A repeated key returns the stored response (`200`), not a second order.
2. In one database transaction: validate the cart, snapshot prices, create the order in `CREATED`, and write an `OrderCreated` row to the **outbox** table.
3. Return `202 Accepted` with the order id.

## Event pipeline (asynchronous part)
- A background `OutboxPublisher` reads unpublished rows and sends them to Kafka topic `orders`. A Redisson lock makes sure only one instance publishes at a time.
- Four consumer groups read `orders`: `inventory-service` (reserves stock), `payment-service` (simulated provider), `order-state-service` (applies state transitions), `cancel-compensation-service` (releases stock after a cancellation).
- On failure a message goes to `orders.retry.5s`, then `.30s`, then `.5m`, and finally `orders.DLQ`. `DlqConsumer` stores dead letters in `dlq_messages`, and an admin can replay one with `POST /admin/dlq/{id}/replay`.
- Before doing any work a consumer inserts the event id into `processed_events` (unique). A duplicate fails the constraint and is skipped, so side effects happen once even though Kafka delivers at least once.

Why these choices: [delivery-guarantee.md](delivery-guarantee.md), [consumer-idempotency.md](consumer-idempotency.md), [distributed-lock-decision.md](distributed-lock-decision.md).

## Data
Flyway migrations `V1` to `V9` (orders, order events, dedupe store, reservations, DLQ messages, outbox metadata, refresh tokens) plus the repeatable `R__seed_data.sql`. Hibernate runs with `ddl-auto: validate`, so the schema can only change through a migration. Optimistic locking protects concurrent stock updates.

## Caching and rate limiting
- Product reads are cached in Redis (10 minutes for one product, 2 minutes for lists) and evicted on write.
- The rate limiter is a token bucket implemented as a Lua script, so the check-and-take is atomic in Redis. Refusals return `429` with `Retry-After`; see [rate-limit-contract.md](rate-limit-contract.md).

## Observability
Actuator health and Prometheus metrics, pipeline counters (`PipelineMetrics`), and the trace id on every log line and every error body.

## Environments
Local development, production differences and their effect are listed in [dev-vs-prod.md](dev-vs-prod.md).
