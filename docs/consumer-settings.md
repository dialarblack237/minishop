# Consumer settings (partitions, groups, offsets)

Reference for Lesson 7.1.2. Records the choices MiniShop makes about how Kafka delivers events to consumers, and why.

## The four terms

- **Partition** — a topic is split into ordered lanes. Each message goes to exactly one partition. Messages within a partition stay in order; messages across partitions do not.
- **Consumer group** — a named set of consumers that together read one topic once. Each partition is assigned to exactly one consumer in the group.
- **Offset** — the sequence number of a message within a partition. Kafka remembers, per group, the last committed offset per partition, so a restart resumes from there.
- **Rebalance** — when a consumer joins or leaves a group, Kafka redistributes partitions. A rebalance can cause the last batch of a partition to be reprocessed by a new consumer.

## MiniShop's settings

| Setting | Value | Why |
|---|---|---|
| **Partitions per topic** | **1** (for `orders` and `orders.DLQ`) | Ordering is guaranteed across all events. No rebalances to debug. Works on a single local Kafka node and on free tiers. The page's "3 partitions" recommendation is for a production-shaped pipeline; v1 on one laptop doesn't need it. If parallelism is needed later, create `orders-v2` with 3 partitions and migrate consumers — never change the partition count of a topic that already has messages. |
| **Consumer groups per stage** | `inventory-service`, `payment-service`, `order-state-service`, `cancel-compensation-service` | One group per stage. Each group reads the topic **independently** — that's how inventory and payment and state machine all see the same `OrderCreated` event. Within a group, each partition is read by exactly one consumer. Combining two stages into one group would mean only one of them ever sees a message. |
| **Offset commit mode** | Manual ack (Lesson 7.1.3 — to be configured in 7.1.3) | The message is committed as processed only after the work succeeds. Auto-ack would commit before the work, so a crash mid-work silently skips the message. |

## What a rebalance does

A rebalance can reprocess the last batch of a partition. This is normal — it's a consequence of "each partition is read by exactly one consumer" plus "a consumer can join or leave at any moment." The dedupe table (Lesson 6.4.2 / 6.4.3) absorbs the duplicate. **Do not try to prevent rebalances** — absorb them with idempotent consumers instead.

## What MiniShop does NOT do

- **No partition count increase** — v1 stays at 1 partition for `orders`. If more parallelism is needed, `orders-v2` is the migration path.
- **No single shared consumer group** — each stage has its own group. One group = one stage.
- **No auto-ack** — see Lesson 7.1.3 for the manual ack configuration.