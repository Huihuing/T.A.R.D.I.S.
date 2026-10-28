# PULSE//ID · T.A.R.D.I.S. ownership proof (feature branch)

**Development only, disabled by default. No live SSO yet.** This bridge allows an already-authenticated T.A.R.D.I.S. member to prove ownership to PULSE//LAB without disclosing passwords or their transfer PIN.

- `POST /api/auth/pulse-link/proof` requires the existing valid Tardis Spring JWT and a 43-character PULSE challenge.
- Returns a signed HS256 assertion valid for 90 seconds with a unique `jti`, exact `iss`/`aud`/`world`, challenge and `member.id` subject.
- The `/pulse-link?challenge=...` page is isolated from existing login/portfolio/wallet/PIN flows and only exposes a proof when authenticated.
- Requires **server-only** `PULSE_LINK_PROOF_SECRET` with ≥32 random UTF-8 bytes; without it the endpoint returns HTTP 503. This secret must match only `PULSE_LINK_TARDIS_SECRET` on the PULSE portal server. Never place it in `VITE_` variables.
- Does not change existing database migrations, Google login, existing JWT issuance, refresh cookie, stock balances, ledger or transfer behavior.
- Does not implement OIDC / single-sign-on for TARDIS. Central portal must verify proof and create link transaction first.
- CI and manual checks: `cd Backend && ./gradlew test`, `cd Frontend && npm ci && npm run build`. Negative tests for no auth, disabled secret, invalid challenge, wrong issuer and user conflicts. No tests have been executed in this branch yet.
- This branch must not be merged/deployed until the portal's matching one-time challenge transaction is verified locally and production rollout is explicitly approved.

Cross-service reference: `Huihuing/pulse-lab` → `docs/PULSE_LINK_RUNBOOK.md`.
