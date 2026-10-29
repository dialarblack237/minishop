# Load test (k6)

One k6 script: `get-products.js`. Ramps to 1 virtual user for 100 seconds and
enforces the read-path SLO from Lesson 1.2.3 — p95 latency under 200 ms, error
rate under 1%.

The current script is the **1-VU** variant, because a 20-VU variant was blocked
by a rate-limiter finding recorded in `../docs/load-baseline.md` (see the
2026-10-29 blocks). Once that finding is fixed, a 20-VU burst is a one-line
change to `stages` in `get-products.js`.

## Run it

From the repo root, in PowerShell:

```powershell
# 1. Start the app in a terminal (keep it open)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 2. In a second terminal, run the load test
.\scripts\load-test.ps1