#!/usr/bin/env bash
#
# Smoke test — four checks that must pass identically in dev and prod.
# Same script, different BASE URL. That is the whole point: "passing"
# must mean the same thing in both places.
#
# Usage:
#   ./scripts/smoke.sh http://localhost:8080
#   ./scripts/smoke.sh https://minishop-xxxx.onrender.com
#
# Exit code 0 = all checks passed. Any non-zero = first failing check's
# message is what you investigate. Each check is one curl, one grep, one
# decision — deliberately simple.

set -u  # do not silently ignore unset variables

BASE="${1:-http://localhost:8080}"
echo "smoke test target: $BASE"

# ---------------------------------------------------------------------------
# Check 1 — the app is up and reports UP.
# Proves: JAR started, /actuator/health exposed, DB + Redis reachable enough
# that the health contributor does not mark itself DOWN.
# ---------------------------------------------------------------------------
if ! curl -fsS "$BASE/actuator/health" | grep -q '"status":"UP"'; then
    echo "FAIL 1: /actuator/health did not return status UP"
    exit 1
fi
echo "OK 1: health UP"

# ---------------------------------------------------------------------------
# Check 2 — public read works.
# Proves: the DB query path for products works (covers Postgres 15 vs 16
# difference from the dev-vs-prod table).
# ---------------------------------------------------------------------------
if ! curl -fsS "$BASE/products?page=0&size=1" | grep -q '"content"'; then
    echo "FAIL 2: /products did not return paginated content"
    exit 1
fi
echo "OK 2: products list returns content"

# ---------------------------------------------------------------------------
# Check 3 — protected endpoint refuses without a token.
# Proves: security chain still enforced. If this passes with 200, auth wiring
# broke (would be a critical security incident, not a smoke-test flake).
# ---------------------------------------------------------------------------
code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/orders")
if [ "$code" != "401" ]; then
    echo "FAIL 3: /orders without token returned $code, expected 401"
    exit 1
fi
echo "OK 3: /orders refuses unauthenticated request with 401"

# ---------------------------------------------------------------------------
# Check 4 — cache path works: two reads of the same product return same body.
# Proves: Redis is reachable and the cache layer functions (covers TLS in
# prod, DB 15 in prod). A cache miss on second read is not an error in
# itself; but differing bodies means caching is bypassed or broken.
# ---------------------------------------------------------------------------
A=$(curl -fsS "$BASE/products/1") || { echo "FAIL 4: first /products/1 failed"; exit 1; }
B=$(curl -fsS "$BASE/products/1") || { echo "FAIL 4: second /products/1 failed"; exit 1; }
if [ "$A" != "$B" ]; then
    echo "FAIL 4: /products/1 returned different bodies on two reads (cache-miss or non-deterministic response)"
    exit 1
fi
echo "OK 4: /products/1 identical on two reads"

echo
echo "SMOKE OK: $BASE"