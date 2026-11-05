# ADR-004 — Managed Kafka provider choice

**Status:** Accepted
**Date:** 2026-11-05
**Context:** Module 12.1.3 — MiniShop v1 needs a free-tier managed Kafka.

## Context

MiniShop v1's async pipeline needs a Kafka broker reachable from Render:

- **Topics needed in v1:** `orders`, `orders.DLQ` — minimum two.
- **Topics that arrive in v2 (7.2.1 retry ladder):** `orders.retry.5s`,
  `orders.retry.30s`, `orders.retry.5m` — total five topics in v2.
- **Region:** need a free-tier region close to `aws-us-east-2` (Ohio),
  where Neon Postgres and (planned) Render will live.
- **Auth model:** Spring Kafka's built-in SASL is acceptable; a custom
  authenticator is not.
- **Cost:** free tier must stay free — a 30-day credit that expires is not
  a free tier.

## Decision

**Redpanda Cloud**, one EU-or-US free-tier region close to Ohio.

## Alternatives considered

- **Confluent Cloud.** REJECTED — free tier is a 30-day, $400 credit,
  not a permanent free plan. Would force a paid plan or a provider
  migration mid-project. Every other dial is fine (latency, auth), but
  "free tier that stops being free" is the kind of surprise the dev-vs-prod
  table exists to prevent.
- **Upstash Kafka.** REJECTED — free tier limits topics per project to one.
  MiniShop needs at least two (`orders` + `orders.DLQ`) and five in v2.
  Latency and auth are fine; limits are the blocking dial.

## Consequences

**Positive:**

- Free tier stays free — no surprise 30-day cliff.
- SASL/SCRAM over TLS — Spring Kafka supports this out of the box (no
  custom authenticator to maintain).
- Provider has regions close enough to `aws-us-east-2` for latency under
  ~50ms round trip.

**Negative:**

- Fewer regions than Confluent; if the project later needs a specific
  region Confluent has and Redpanda doesn't, migration means recreating
  topics and re-pointing the app.
- Free-tier message quota still finite; must monitor in v2 (retry topics
  increase traffic).

## Revisit when

- Message quota becomes a limiting factor (measure with Micrometer
  `kafka.producer.*` and `kafka.consumer.*` — already exposed).
- A v2 feature requires a provider-specific feature Confluent offers
  (e.g., ksqlDB).
- The project moves off free tier — at which point the choice can be
  re-evaluated from a cost, not "is it free" perspective.

## Provisioning status

**Not yet provisioned.** Creating the Redpanda free-tier cluster and
copying `KAFKA_BOOTSTRAP_SERVERS` / `KAFKA_SASL_USER` / `KAFKA_SASL_PASSWORD`
into Render env vars is deferred to Module 12.2 (App Hosting), where the
first actual deploy makes them necessary. Until then, the decision sits
here, documented, and nothing is broken by its absence.