# Neon Postgres — provisioned for MiniShop prod

Created for Lesson 12.1.1. This file records **what exists** and **what still
needs doing** — it contains no secret values. The actual connection string
lives in GitHub's `prod` environment secrets (created in 11.2.2 Half A) or in
Render's environment variables (created in 12.2.1).

## What exists

- Provider: Neon (free tier)
- Project: `minishop-prod`
- Region: `aws-us-east-2` (Ohio) — **Render must be deployed in the same region**
- Postgres version: **15** (chosen at project creation; Neon locks the major version per project)
- Database name: `neondb` (Neon's default; not renamed)

## The connection string shape

Neon's connection string has four parts, in a fixed order:

    postgresql://USER:PASSWORD@HOST:PORT/DATABASE?sslmode=require

The password is the secret; the host and database name are provider-assigned.
The `?sslmode=require` at the end is NOT optional for Neon — without it, the
password crosses the wire in plaintext.

**The actual URL is not in this file and never will be.** It goes into
GitHub secrets (repository or `prod` environment), where CI reads it. It goes
into Render env vars (Module 12.2), where the app reads it.

## What `application-prod.yml` needs

The file already uses the right shape — see `src/main/resources/application-prod.yml`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      connection-timeout: 5000
      validation-timeout: 2000