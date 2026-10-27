# Distributed lock decision — MiniShop v1

Reference for Lessons 8.3.1 and 8.3.2. Records which "must not run twice" cases in MiniShop use a distributed lock, and which use a cheaper alternative.

## The four doors (choose first that applies)

Before reaching for a distributed lock, ask each of these in order:

### Door 1 — UNIQUE constraint (the database enforces "once")

- **Where it's used:** `customers.email` (5.1.2), `processed_events.event_id` (6.4.2), `outbox.event_id` (6.1.3), `reservations (order_id, product_id)` (6.3.4), `idempotency_keys.idem_key` (6.4.1).
- **Why:** the database refuses the second insert, atomically. No code needed, no extra dependency.
- **When to use:** any "must be exactly one of these rows" case.

### Door 2 — Idempotency key from the caller

- **Where it's used:** `POST /orders` with `Idempotency-Key` header (6.4.1).
- **Why:** the caller generates one key per logical action; a retry with the same key replays the original response, never a second order.
- **When to use:** customer-facing write endpoints where a network retry must not produce a duplicate. **This is the answer for every write endpoint in MiniShop.**

### Door 3 — State machine refuses duplicate transitions

- **Where it's used:** order state transitions (6.3.3). `OrderStateMachine.apply(from, event)` throws `IllegalStateTransition` if the pair is not on the allowed table.
- **Why:** even if a duplicate event arrives, the state machine refuses to apply it. No lock needed.
- **When to use:** any operation whose allowed transitions are enumerable (state machines, workflow engines).

### Door 4 — Distributed lock (the last resort)

- **Where it's used:** `OutboxPublisher.publishUnsent` (8.3.1). Redisson `RLock` on `lock:outbox-publisher`.
- **Why here and nowhere else:** publishing to Kafka + marking the outbox row published is a **two-write** operation with no transaction wrapping both. Two instances would race between `findUnsent` and `markPublished` and publish the same event twice. The alternatives don't apply:
  - No UNIQUE constraint can express "this row should be published exactly once across all instances" — a `published` boolean is a cache, not a guard; two instances can both read `published=false`.
  - Idempotency key is a caller-side concept; the publisher has no caller.
  - There's no state machine here — it's a side-effect, not a transition.
- **When to use:** operations that (a) write to two systems atomically, (b) have no caller-provided idempotency key, and (c) cannot be expressed as a UNIQUE constraint or state transition. **This is rare.** In MiniShop v1, it's exactly one place.

## The rule

> **Reach for a lock LAST, not first.** A lock is the only solution in this list that makes throughput *worse* and adds a new dependency (Redis) to the correctness story. Use it only when doors 1–3 genuinely don't apply.

## What a lock actually costs

| Cost | Detail |
|---|---|
| Serialisation | Every request that takes the lock waits for every other request on the same lock. The lock becomes a throughput ceiling. |
| New dependency on Redis | Redis must be up for *correctness*, not just speed. A Redis outage becomes a correctness outage. |
| Confusing failure modes | What if the lease expires mid-work? What if two clients disagree about ownership? |
| Harder to test | Race conditions depend on timing and Redis availability. |

## The one-question test

Before writing a lock, ask:

> **Can the underlying operation be made idempotent?**
>
> - **Yes, via UNIQUE constraint** → no lock.
> - **Yes, via caller-supplied idempotency key** → no lock.
> - **Yes, via state machine refusal** → no lock.
> - **No, and it must not run concurrently** → lock is the right answer.

## Scope of locks in MiniShop v1

Exactly one:
- `lock:outbox-publisher` — protects the scheduled publisher.

Not locked (deliberately):
- Order creation — `Idempotency-Key` header (door 2).
- Inventory reservation — `UNIQUE (order_id, product_id)` on `reservations` (door 1), plus `findByIdForUpdate` (row-level pessimistic, not distributed).
- Order state transitions — `OrderStateMachine` refuses duplicates (door 3), plus `findByIdForUpdate` (row-level, not distributed).
- Cache invalidation — `@CacheEvict` is idempotent by nature; deleting a key twice is a no-op.
- Rate limiting — the Lua script is already atomic (8.2.1).
- Idempotency storage — `UNIQUE` on `idempotency_keys.idem_key` (door 1).

## When to revisit

If MiniShop scales to more publisher-instance producers of outbox rows, or if the publisher ever grows to include work that a lock can't express (e.g., a side-effect on a legacy non-idempotent system), revisit:
- Add per-row locks? (Smaller scope than per-publisher.)
- Move the publisher into a dedicated service that runs on one instance by design?
- Migrate to a messaging system with built-in dedupe (SQS FIFO, Kafka transactions)?

None of these are needed for v1.