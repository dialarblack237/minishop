# Delivery guarantee in MiniShop

Reference for Lesson 7.1.1. Records the delivery guarantee MiniShop runs on, why the other two were rejected, and how the pipeline's effective guarantee is exactly-once side effects.

## The three guarantees

| Guarantee | Loses an order? | Duplicates? | Fit for MiniShop? |
|---|---|---|---|
| At-most-once | **Yes — silently** | Never | **No.** Lost order is invisible: customer paid, nothing downstream ever ran. Duplicates are visible and idempotent-safe; loss is neither. |
| At-least-once | Never | Sometimes | **Yes.** The base guarantee. Consumers use the dedupe table (Lesson 6.4.2) so duplicates are harmless. |
| Exactly-once | Never (in theory) | Never (in theory) | **Slogan.** Not a real messaging guarantee in the general case. Achieved only as at-least-once + idempotent consumer. |

## What MiniShop runs

- **Messaging layer:** Kafka at-least-once. Every consumer may see a duplicate — from a publisher retry (6.2.2), a Kafka redelivery after a crash, a consumer restart, or a manual replay from Kafbat UI.
- **Application layer:** every consumer claims the event's `eventId` in the `processed_events` table before doing work (Lesson 6.4.2 / 6.4.3). A duplicate claim fails the UNIQUE constraint and the consumer returns early.
- **Effective guarantee:** **exactly-once side effects.** The message itself may be delivered more than once; the stock decrement, payment charge, and state transition each happen exactly once.

## The sentence

> MiniShop runs at-least-once delivery; every consumer is idempotent (dedupe on `eventId`), so the effective guarantee for every side effect is exactly-once.

## Why not Kafka EOS (transactional producer)

Kafka's "exactly-once semantics" (EOS) is a real feature, but:

1. It only guarantees atomicity between Kafka reads and Kafka writes. It **cannot** make an HTTP call to a payment provider atomic with a Kafka send.
2. It cannot make a database write atomic with a Kafka send — that's the dual-write problem the outbox pattern (Lesson 2.1.3) exists to avoid.
3. It is harder to run on the free tiers (Module 12.1.3) — extra config, tighter limits.
4. It is more code and more failure modes than a dedupe table.

The dedupe table is one indexed `INSERT` per event. EOS debugging is hours of unfamiliar config. The trade-off is not close.

## The test that proves it

```java
@Test
void duplicate_event_produces_one_side_effect() {
    consumer.onEvent(event);     // first delivery — stock goes from 100 to 99
    consumer.onEvent(event);     // duplicate — dedupe refuses, no change
    assertThat(stockOf("coffee")).isEqualTo(99);  // exactly one decrement
}