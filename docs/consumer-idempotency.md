# Consumer-side idempotency in MiniShop

Reference document for Lessons 6.4.2 and 6.4.3. The dedup store and
the standard consumer shape described in those lessons are already
implemented (Lessons 6.3.1–6.3.4). This file records them in one place
so a reviewer can find the pattern without tracing every consumer.

## What "consumer-side idempotency" means here

Kafka delivers at-least-once — every event will arrive, possibly more
than once. A duplicate can come from any of:

1. The outbox publisher retried the send (Lesson 6.2.2).
2. Kafka redelivered after a consumer crash mid-processing.
3. A consumer restarted and re-read an uncommitted offset.
4. A human replayed a message from Kafbat UI.

The dedup store handles all four the same way, because all four end in
the same operation: an `INSERT` of the event's `event_id` into a table
with a UNIQUE constraint.

## The dedup store

Table: `processed_events` (migration `V3__dedupe_store.sql`).

```sql
CREATE TABLE processed_events (
  event_id     UUID PRIMARY KEY,
  event_type   TEXT NOT NULL,
  processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);