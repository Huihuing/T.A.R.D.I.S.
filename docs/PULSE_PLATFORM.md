# PULSE authentication entry — T.A.R.D.I.S.

The floating PICO World Shell was removed after real screenshots showed overlap with native login/signup controls. Do not reintroduce a fixed-position platform badge.

Existing /login and /register are the entry points. With `VITE_PULSE_AUTH_ENABLED=true` they route to the reviewed portal's /login or /signup with a fixed World identifier. The build-time portal origin is `VITE_PULSE_LAB_URL` (default: the canonical PULSE portal). This flag is OFF by default and must remain off until the portal is accessible, production OIDC/DB and native SSO are deployed and actual round-trip login is verified. Merely configuring the flag does not implement SSO.

Native login, account recovery, incoming verification/proof/SSO state and `?nativeAuth=1` remain local. These exceptions change navigation only, not permissions. No passwords, emails, session tokens or arbitrary return URLs are transferred. Existing member IDs, wallet/PIN and service-owned DB remain unchanged. Central registration does not automatically create or merge a Tardis account.

Browser CI verifies the removal and both entry modes at desktop/390px/320px widths. The central target is a UI routing fixture; these checks do not replace real OIDC or production SSO tests. No backend migration or operational secret is changed.
