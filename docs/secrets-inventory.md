# Secrets Inventory

One row per secret MiniShop owns. Every value lives in the hosting dashboard
(Render / GitHub Environments), **never** in the repository. Rotation is a
dashboard change plus a redeploy — no git history rewrite.

| # | Secret | Env var name | Where it comes from | Rotation cost |
|---|--------|--------------|---------------------|---------------|
| 1 | Database URL (contains auth) | `DATABASE_URL` | Neon dashboard → Connection string | Restart with new URL; no data migration |
| 2 | Database user | `DATABASE_USER` | Neon dashboard → Roles | Create new role, update dashboard, restart |
| 3 | Database password | `DATABASE_PASSWORD` | Neon dashboard → Roles → reset password | Reset password, update dashboard, restart |
| 4 | Redis URL (contains token on Upstash) | `REDIS_URL` | Upstash dashboard → REST URL with token | Regenerate token, update dashboard, restart |
| 5 | Kafka SASL credentials | `KAFKA_SASL_USER`, `KAFKA_SASL_PASSWORD` | Redpanda / Confluent / Upstash Kafka dashboard | Regenerate SASL credentials, update dashboard, restart |
| 6 | JWT signing key | `JWT_SIGNING_KEY` | Generated once on local machine (`openssl rand -base64 48`), pasted into dashboard | **Logs everyone out** — every existing token becomes invalid |

## Not secrets

- `app.jwt.secret` in `application-dev.yml` — documented dev-only value, used on the
  developer's local machine only; grants nothing outside it. Module 12 wires a real
  `JWT_SIGNING_KEY` for prod.
- Seed bcrypt hashes in `R__seed_data.sql` — public test credentials documented in the brief.
- Topic names, port numbers, H2 in-memory URLs.

## The rule

A secret committed to git is a secret you assume is already leaked. The only safe
response is rotation at the source **first**, then a new commit. Rewriting history is
optional; rotation is not.

## Where secrets are scanned

- **GitHub secret scanning** (Settings → Code security → Secret scanning → Enable) —
  alerts on push, server-side.
- **CI job** (Module 11) — fails the build on a hit, client-side, before merge.