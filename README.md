# MiniShop

An event-driven online shop backend. Customers place orders through a REST API; each order then moves through a Kafka pipeline (stock reservation, payment, confirmation) with retries, a dead-letter queue and idempotent consumers, so a crash or a duplicate message never charges or ships twice.

![CI](https://github.com/dialarblack237/minishop/actions/workflows/ci.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f)
![License: MIT](https://img.shields.io/badge/license-MIT-green)

> **Status:** backend complete and deployed-ready; Kubernetes manifests included. The React storefront (MiniShop Front) is a separate planned project.

## What it demonstrates

- **Order pipeline over Kafka**: `orders` topic with consumer groups for inventory, payment, order state and cancel compensation, three delayed retry topics (`5s`, `30s`, `5m`) and an `orders.DLQ` dead-letter topic with an admin replay endpoint.
- **Transactional outbox**: events are written in the same database transaction as the order and published by a background publisher guarded by a Redisson distributed lock.
- **Exactly-once side effects**: delivery is at-least-once; every consumer claims the event id in a `processed_events` table first, so duplicates are harmless.
- **Idempotent order placement**: `Idempotency-Key` header; a repeated key replays the original response.
- **Order state machine**: `CREATED -> RESERVED -> PAID -> CONFIRMED`, with `CANCELLED` and `REFUNDED`; illegal transitions are refused and tested table-driven.
- **Security**: JWT access tokens (15 min) and refresh tokens (30 days) that can be revoked, with token families, role-based access (`ADMIN`, `CUSTOMER`), BCrypt.
- **Redis**: product cache (10 min single, 2 min list, evicted on write) and a Lua token-bucket rate limiter returning `429` with `Retry-After` and `X-RateLimit-*` headers (RFC 7807 problem body with a trace id).
- **Database**: PostgreSQL in production, H2 for local development, schema owned by Flyway (9 versioned migrations), optimistic locking.
- **Operations**: Actuator health and Prometheus metrics, trace id on every log line, k6 load test, smoke test script, OpenAPI contract test.

## Architecture

```
Client --> REST API (Spring MVC, JWT, rate limit)
              |
              +--> PostgreSQL  (orders, outbox, processed_events, reservations, dlq_messages)
              +--> Redis       (cache, rate-limit buckets, distributed lock)
              |
              v  outbox publisher
           Kafka topic "orders"  --> inventory-service
                                 --> payment-service
                                 --> order-state-service
                                 --> cancel-compensation-service
              |  failure
              v
        orders.retry.5s -> 30s -> 5m -> orders.DLQ (stored, replayable by an admin)
```

More detail: [docs/architecture.md](docs/architecture.md), [docs/delivery-guarantee.md](docs/delivery-guarantee.md), [docs/distributed-lock-decision.md](docs/distributed-lock-decision.md).

## Tech stack

Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation, Actuator), Spring Kafka, Spring Data Redis, Redisson, Flyway, PostgreSQL / H2, JJWT, springdoc-openapi, Micrometer + Prometheus, JUnit 5, Mockito, JaCoCo, k6, Docker, GitHub Actions, Kubernetes.

## Quick start (local)

Requirements: JDK 21 and Docker (for Redis and Kafka). Maven is included.

```bash
git clone https://github.com/dialarblack237/minishop.git
cd minishop

# 1. Redis + Kafka (single node, KRaft)
docker compose up -d

# 2. The app, dev profile (H2 in-memory database, seeded)
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Smoke test: `./scripts/smoke.sh http://localhost:8080`

Seeded accounts: `admin@minishop.local` (ADMIN) and `sam@minishop.local` (CUSTOMER). The development password is defined by the seed data; see [docs/api.http](docs/api.http).

### Try it

```bash
# log in
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"sam@minishop.local","password":"<dev password>"}'

# list products (public)
curl -s 'localhost:8080/products?page=0&size=5'

# place an order (needs the token and an Idempotency-Key)
curl -s -X POST localhost:8080/orders \
  -H "Authorization: Bearer <accessToken>" -H 'Idempotency-Key: demo-001' \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantity":1}]}'
```

The order returns `202 Accepted` in state `CREATED`; the pipeline then moves it asynchronously.

## API

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/login` | public | Email and password, returns access and refresh tokens |
| POST | `/auth/refresh` | public | Exchange a refresh token for a new pair |
| POST | `/auth/logout` | public | Revoke a refresh token |
| GET | `/products`, `/products/{id}` | public | Paginated list (whitelisted sort), cached single product |
| POST | `/products` | ADMIN | Create a product |
| PUT | `/products/{id}` | ADMIN | Update a product |
| POST | `/orders` | signed in | Place an order (`Idempotency-Key` required) |
| POST | `/admin/dlq/{id}/replay` | ADMIN | Replay a dead-lettered message |
| GET | `/actuator/health`, `/actuator/prometheus` | see note | Health and metrics |
| GET | `/swagger-ui.html`, `/v3/api-docs` | public | Interactive API documentation |

The full contract is in [`src/main/resources/openapi.yaml`](src/main/resources/openapi.yaml) and is checked by `OpenApiContractTest` in CI. Rate-limit behaviour for clients: [docs/rate-limit-contract.md](docs/rate-limit-contract.md).

## Configuration

Profiles: `dev` (default), `prod`, `test`. Production reads everything from environment variables (see [`.env.example`](.env.example) and [docs/secrets-inventory.md](docs/secrets-inventory.md)).

| Variable | Description |
|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `REDIS_URL` | `redis://` or `rediss://` URL |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_USER`, `KAFKA_SASL_PASSWORD` | Kafka over SASL_SSL |
| `JWT_SIGNING_KEY` | At least 32 characters; generate with `openssl rand -base64 48` |
| `PORT` | HTTP port (default 8080) |
| `SPRING_PROFILES_ACTIVE` | `prod` in production |

Never commit real values. The only secrets in the repository are documented development-only values.

## Docker

```bash
docker build -t minishop .
docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=... -e DATABASE_USER=... -e DATABASE_PASSWORD=... \
  -e REDIS_URL=... -e KAFKA_BOOTSTRAP_SERVERS=... \
  -e KAFKA_SASL_USER=... -e KAFKA_SASL_PASSWORD=... -e JWT_SIGNING_KEY=... minishop
```

## Kubernetes

Manifests are in [`k8s/`](k8s/): namespace, config map, secret template, deployment (probes, resource limits), service, ingress and horizontal pod autoscaler. The app uses managed PostgreSQL, Redis and Kafka (Neon, Upstash, Redpanda in the original setup), so only the API runs in the cluster. See [k8s/README.md](k8s/README.md).

## Tests

```bash
./mvnw verify
```

- Unit tests: state machine (table-driven), outbox row, seed data, service atomicity, publisher.
- Integration tests: Kafka consumers, Redis cache, HTTP contract, admin endpoint security, sort whitelist.
- Contract test: the running API must match `openapi.yaml`.
- JaCoCo coverage gate in `pom.xml` (currently 34%); the plan to raise it to 75% is in [docs/coverage-plan.md](docs/coverage-plan.md).

Two older test classes are excluded from the build and are tracked in [docs/known-issues.md](docs/known-issues.md).

## Load test

```bash
./mvnw spring-boot:run
k6 run k6/get-products.js
```

Results and the finding that limited the first run: [docs/load-baseline.md](docs/load-baseline.md).

## Project structure

```
src/main/java/com/example/minishop/
  config/      security, JWT filter, rate-limit filter, CORS, cache, trace id
  domain/      entities, order state machine, outbox row
  event/       event records (OrderCreated, PaymentSucceeded, ...)
  repository/  Spring Data JPA
  service/     business logic, Kafka consumers, outbox publisher, rate limiter
  web/         controllers and DTOs
src/main/resources/db/migration/   Flyway V1..V9 and seed data
docs/   k6/   scripts/   k8s/
```

## Known limitations

See [docs/known-issues.md](docs/known-issues.md). In short: orders are currently attributed to the admin account instead of the logged-in user, the metrics endpoint is not protected, there is no public registration endpoint and no storefront yet.

## Author

**Alvares Arcel DIFFOUO**, Douala, Cameroon. GitHub: [@dialarblack237](https://github.com/dialarblack237)

License: MIT (2026).
