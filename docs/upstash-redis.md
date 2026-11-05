# Upstash Redis — provisioned for MiniShop prod

Created for Lesson 12.1.2. This file records **what exists** and **what
still needs doing** — it contains no secret values. The actual connection
URL lives in Render environment variables (Module 12.2), where the app
reads it as `REDIS_URL`.

## What exists

- Provider: Upstash (free tier)
- Database name: `minishop-prod`
- Region: `us-east-1` — closest free-tier region to `aws-us-east-2` (Ohio),
  where Neon and Render will live. Chosen for minimum cross-region latency
  even though it is not the same region string.
- TLS: enabled
- Eviction policy: **`allkeys-lru`** — when the cache reaches its memory
  cap, it evicts the least-recently-used key to make room. `noeviction`
  would be wrong here: it makes writes fail on a full cache, which turns
  "cache is full" into "cache is broken."
- TTL: default from Upstash, but the app sets per-key TTL from Module 8.1.1
  (10m for `product:{id}`, 2m for `product:list:*`). Upstash's own expiry
  is a second safety net, not the primary one.

## The connection URL shape

Upstash gives one URL with the token embedded:

    rediss://default:TOKEN@HOST.upstash.io:6379

Read it in four parts:
- **`rediss://`** — TWO s's. Means TLS. **`redis://` with one s is rejected
  by Upstash** with "SSL required". This is the #1 cause of "cache dead in
  prod while working in dev".
- **`default:TOKEN`** — the token is the secret. Lives in Render env vars
  (12.2), never in a committed file.
- **`HOST.upstash.io`** — provider-assigned. Do not retype; copy from the
  Upstash dashboard's "Connect" → "redis-cli" tab, where the whole URL is
  already assembled with the token in it.
- **`:6379`** — standard Redis port.

**The actual URL is not in this file and never will be.** It goes into
Render env vars as `REDIS_URL`, read by `application-prod.yml`.

## What `application-prod.yml` needs

The file already uses the right shape — see
`src/main/resources/application-prod.yml`:

```yaml
spring:
  data:
    redis:
      url: ${REDIS_URL}
      timeout: 5s