# Load-test baseline

The k6 script that produces these runs is documented in
[`../k6/README.md`](../k6/README.md).

Every load test writes one dated block here, with the workload, the cache
state, the numbers, and the SLO the numbers are compared against. Future runs
are compared against the most recent block for the **same** workload.

## 2026-10-29 — 20-VU burst, k6 get-products.js (FINDING)

**Workload:** 20 VUs, 15 s sleeps, 60 s hold.
**Read endpoint:** `GET /products?page=0&size=20&sort=priceCents,asc`.
**Cache state:** warm (products cached from previous reads).

**p95 latency:** unmeasurable — one successful sample at 95.11 ms.
**Error rate:** 99.14% (429s from `RateLimitFilter`).
**Finding:** the `RateLimitFilter` returned 429 for 116 of 117 requests at
~1 req/s, against a policy of `Limit(100, false)` for GET. The exact cause is
not yet isolated. See PR for 10.4.1 (the merge commit that closed that lesson)
for the full chain analysis. Fix deferred to a `fix(rate-limit)` PR.

**Not a baseline.** Recorded here so the next burst run's numbers are
compared against a known-good burst — which this is not.

## 2026-10-29 — 1-VU steady state, k6 get-products.js

**Workload:** 1 VU, 1 s sleep, 100 s hold.
**Read endpoint:** `GET /products?page=0&size=20&sort=priceCents,asc`.
**Cache state:** warm after the first read.

**p95 latency:** <fill after the run>  (SLO: < 200ms)
**Cache hit ratio:** <fill after the run>  (SLO: ≥ 80%)
**Error rate:** <fill after the run>  (SLO: < 1%)
**Throughput:** <fill after the run> rps (informational)

**Notes:** single-VU baseline — measures per-request steady-state latency,
not the 20-VU burst. The 20-VU burst is blocked on the `fix(rate-limit)` PR
above.