# PULSE platform integration — T.A.R.D.I.S.

Current decision: 2026-11-02. Central PULSE//ID, independent World databases. Physical MySQL consolidation is deferred and is not required for this UI release.

The compact MARKET PICO navigation is frontend-only. It opens the portal, other Worlds and the central identity-management screen in a new tab so current work remains intact. It never reads credentials, modifies login state, calls a World API or claims an account is linked.

`VITE_PULSE_LAB_URL` can select a reviewed HTTPS portal origin at build time. Invalid configuration disables the shell. Local HTTP is allowed only in a development build. The default is the team's PULSE portal alias. Do not derive this setting from URL query parameters.

Tardis member IDs, its database, virtual wallet/ledger, PIN verification and all native login/refresh flows remain unchanged. The separate `feature/pulse-id-link-bridge` PR implements SSO and requires actual integration and operational migration checks before release. Deploying this navigation must not deploy that pending backend/schema work.

The common source is versioned as PULSE World Shell 1.0.0; keep its registry and behavior synchronized with the portal and the other two World copies. Browser CI checks real native frontend desktop/mobile navigation with the backend deliberately unavailable; it is not proof of production SSO.
