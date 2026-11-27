# Known issues and roadmap

Honest list of what is not finished. Items are ordered by importance.

## Should be fixed first
1. **Orders are attributed to the admin account.** `OrderController.currentCustomer()` looks up `admin@minishop.local` instead of using the authenticated user. Any signed-in customer places orders as the admin. Fix: resolve the customer from the security context (the JWT subject) and add a test.
2. **Metrics endpoint is public.** `SecurityConfig` permits `/actuator/**`. Health can stay public; `/actuator/prometheus` and `/actuator/metrics` should be restricted (network policy, separate management port or authentication).
3. **Two test classes are excluded from the build** (`CustomerServiceTest`, `OrderServiceUnitTest`) because of a Flyway slice problem and a class-loading order problem under JaCoCo. The planned fixes are in [coverage-plan.md](coverage-plan.md). Until they are repaired, those tests prove nothing.
4. **Coverage gate is low (34%).** The step-by-step plan to reach 75% is in [coverage-plan.md](coverage-plan.md).

## Improvements
- No public registration endpoint, although `CustomerService.register` exists.
- The Docker image runs as root; add a non-root user and then enable `runAsNonRoot` in `k8s/deployment.yaml`.
- The CI deploy job is not wired yet: see [ci-deploy-todo.md](ci-deploy-todo.md).
- The 20-user load test was blocked by the rate limiter (see [load-baseline.md](load-baseline.md)); the limit for anonymous reads needs tuning.
- Payment is a simulator (`app.payments.simulator.mode`), not a real provider.
- The storefront (MiniShop Front) is a separate project.
