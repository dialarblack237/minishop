# Dev vs Prod — differences that matter

Written for Lesson 11.2.1. The purpose of this file is not to *eliminate*
differences between local dev and Render prod. It is to *know* them in
advance, and to be able to say — for any difference in this table — whether
it is harmless, matters for a subset of bugs, or matters a lot.

Any change to how MiniShop runs should update this table first. If a
difference is not in this table, it is not officially "known".

## Component-by-component

| Component      | Dev (local)                                | Prod (Render + managed)                                       | Matters? |
|----------------|--------------------------------------------|----------------------------------------------------------------|----------|
| Java version   | JDK 21 (Temurin)                            | JDK 21 (Render runtime default for Spring Boot)                | No — same, per Module 3.1.1 |
| Database       | Postgres 15, localhost:5432                 | Neon free-tier Postgres 15, TLS required, connection URL in env var | **Yes** — a Postgres 16-only feature that "worked in dev" will fail on 15. Smoke test exercises a real query. |
| Redis          | Redis 7 (Memurai on Windows), localhost:6379, no TLS, no auth | Upstash Redis, TLS + token, `rediss://` scheme                  | **Yes** — TLS misconfig is the classic "works in dev, fails in prod". Client must use `rediss://` (extra `s`). |
| Kafka          | Kafka 3.9 KRaft localhost:9092, no auth, 1 partition | Redpanda / Upstash Kafka free tier, SASL + SSL, 3 partitions | **Yes** — SASL auth and partition count change consumer-group behaviour; consumer sees 3 partitions. |
| JWT signing key | Dev key from `application-dev.yml`         | Prod key in Render env var, never in repo                      | **Yes** — a token signed in dev will NOT verify in prod. By design (Module 9.1.1). |
| Network        | localhost:8080, HTTP                       | `https://minishop-xxxx.onrender.com`, HTTPS only               | **Yes** — CORS must name the exact prod origin; cookies and Authorization headers must travel over HTTPS only. |
| Cold start     | Instant (JVM already warm)                  | 30–60s after idle on free tier                                 | **Yes** — affects UX and the health-check timeout in the deploy job (11.1.4). |

## Rows that matter = checks in the smoke test

The four rows marked **Yes** that the smoke test in `scripts/smoke.sh` actually exercises:

1. The app is up at `/actuator/health` → proves the JAR started, DB connected, Redis reachable enough for the health contributor.
2. `GET /products?page=0&size=1` → proves the DB query works (Postgres 15 vs 16 in prod).
3. `GET /orders` without a token → proves 401 is actually the answer (auth wiring intact).
4. `GET /products/1` twice → proves the cache path works (Redis reachable, TLS in prod).

Everything else in the table is either identical (Java) or checked by another means (JWT key by the auth test in Module 10, CORS by frontend integration in Module 12).

## Intentional differences (do not fix)

- **Dev runs HTTP, prod runs HTTPS.** Local TLS is not worth the certificates.
- **Dev has no SASL, prod has SASL.** Free managed Kafka requires auth; local KRaft does not.
- **Dev JVM warm, prod JVM cold.** Free-tier hosting has to idle; pay-tier does not.

These are not bugs to eliminate; they are the price of a local-first, free-tier project. The smoke test *accounts* for them, rather than the pipeline pretending they don't exist.

## Smoke test location and usage

`scripts/smoke.sh <BASE_URL>` — four `curl` checks, all fail-fast, same shape in dev and in prod. Run locally:

    ./scripts/smoke.sh http://localhost:8080

Run against deployed (only after Module 12.2 makes the URL real):

    ./scripts/smoke.sh https://minishop-xxxx.onrender.com

Deferred to Module 11.2.2: making the CI workflow run the smoke test against the live URL, so "green" means "smoke passed", not just "build passed".