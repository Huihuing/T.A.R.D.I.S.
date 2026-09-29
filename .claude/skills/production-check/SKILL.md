---
name: production-check
description: Read-only health check of the production deployment (Render backend first, Aiven or Vercel only when needed) after a main merge or when production misbehaves.
disable-model-invocation: true
context: fork
---

Check production health without changing anything. $ARGUMENTS may name a commit, PR or symptom to focus on.

Rules:

- Read-only. Never deploy, redeploy, restart, change settings or env vars, or run write queries. If a fix needs any of those, report it and stop.
- Go in order and stop as soon as the question is answered. Do not open Aiven or Vercel unless the step below calls for it.
- Never print secrets or connection strings.

Steps:

1. **Expected state**: `git log origin/main --oneline -5` and the newest file in `Backend/src/main/resources/db/migration/` (expected Flyway version).
2. **Public endpoints** (no MCP needed):
   - `curl -s -o /dev/null -w "%{http_code}" https://t-a-r-d-i-s.onrender.com/actuator/health/readiness` → 200
   - `curl -s https://t-a-r-d-i-s.onrender.com/actuator/health` → `UP` (includes DB)
   - an anonymous protected API such as `curl -s -o /dev/null -w "%{http_code}" https://t-a-r-d-i-s.onrender.com/api/account/settings` → 401
   The first request after idle can take over a minute (Render free cold start); retry once before calling it a failure.
3. **Render** (only if step 2 fails or the question is about a deploy): if a Render tool is connected, read the latest deploy status and logs for the expected commit — build success, Flyway "Successfully applied"/"up to date" at the expected version, no Hibernate schema-validation error, status `live`. If no Render tool is connected, ask the user for the deploy status and the relevant log lines.
4. **Aiven** (only if logs point at the DB: connection timeouts, Hikari errors): read service state and recent service logs.
5. **Vercel** (only if the question is about the frontend or a frontend bundle was deployed with user approval): read the latest production deployment state. Do not check Vercel for backend-only merges.

Output: one line per checked item (OK / FAIL + evidence), the likely cause for any FAIL, and the next action the user must take.
