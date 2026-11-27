# GitHub metadata: minishop

- **Repository name:** minishop (already exists; keep it)
- **Description (<=120 chars):** Event-driven shop backend: Spring Boot, Kafka outbox and DLQ, Redis, JWT, Flyway, Docker, Kubernetes.
- **Topics:** java, spring-boot, kafka, redis, postgresql, jwt, flyway, event-driven, outbox-pattern, kubernetes, docker, github-actions, portfolio
- **Website:** (leave empty)
- **Commit message:**

```
docs: add README, architecture, known issues, local compose and Kubernetes manifests
```

## Portfolio card

**MiniShop** - An event-driven online shop backend. Orders are accepted idempotently, written with a transactional outbox, and processed through a Kafka pipeline (inventory, payment, order state, compensation) with retry topics, a dead-letter queue and idempotent consumers. JWT with refresh tokens, Redis cache and rate limiting, Flyway migrations, Prometheus metrics, k6 load test, Dockerfile and Kubernetes manifests. Stack: Java 21, Spring Boot 3.5, Kafka, Redis, PostgreSQL.
