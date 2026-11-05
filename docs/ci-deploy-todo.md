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