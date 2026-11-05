# CI deploy job — TODO

Lesson 11.1.4 requires a Render deploy hook and a live `minishop` service.
Neither exists yet. `deploy` job is **deferred to Module 12.2**.

## To do during Module 12.2

1. Create Render service `minishop` (JAR deploy, env vars from Module 12.1).
2. Copy the deploy hook URL from Render dashboard → Settings → Deploy Hook.
3. In GitHub: Settings → Secrets and variables → Actions → New repository secret
   - Name: `RENDER_DEPLOY_HOOK_URL`
   - Value: the hook URL from Render
4. Add the `deploy` job to `.github/workflows/ci.yml` (page 11.1.4 has the exact YAML).
   - Replace `https://your-service.onrender.com` with the real service URL.
   - Set `needs: [build, contract]`.
   - Set `if: github.ref == 'refs/heads/main'`.
5. First fire: only when a commit reaches `main`. Push to `main` → watch the deploy job run.
6. Verify the health check step passes (200 from `/actuator/health`).

## GitHub Environments — already created (Module 11.2.2 Half A)

Completed before Module 12. When you get to 11.1.4's deploy job, do **not** recreate them:

- `staging` — exists, no protection rules.
- `prod` — exists, required reviewer: `dialarblack237`.
  - Approval rule is set. A deploy job targeting `prod` will pause and wait for an approval click.
  - Wait timer: not set (solo project — the approval click is the deliberate act).
  - Deployment branches: "All branches" currently. Module 12 may restrict to `main` once the deploy job is in place.

What still needs doing at Module 12.2:

1. Add the `deploy` job to `ci.yml` with `environment: prod` (11.2.2 Half B).
2. Create the `RENDER_DEPLOY_HOOK_URL` secret **inside** the `prod` environment, not at repository level.
3. Replace `https://your-service.onrender.com` in the health check with the real Render URL.