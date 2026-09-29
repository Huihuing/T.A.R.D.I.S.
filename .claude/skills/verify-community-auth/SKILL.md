---
name: verify-community-auth
description: Verify the current branch's changes to authentication and community features (JWT, refresh cookie, Google login, email codes, guest passwords, post/comment permissions, rate limiting, moderation), then run the backend tests.
disable-model-invocation: true
context: fork
agent: auth-community-reviewer
---

Verify the auth/community changes on the current branch.

1. Collect the diff: `git diff origin/main...HEAD` plus uncommitted changes. If $ARGUMENTS names files, commits or a PR, use those instead.
2. Keep only files in auth, security, account security, email code, board/comment, report/admin, rate limit or `Frontend/src/auth.ts`. If none, report "no auth/community changes" and stop.
3. Review them against your checklist: JWT-only identity, anonymous 401 coverage in `SecurityConfig`, refresh cookie and no auto-replay of mutating requests, email code attempt/expiry commit and send quota, no account enumeration, guest password hashing and edit/delete permission, rate-limit policy for new abuse-prone endpoints, no secrets in logs/responses.
4. Check that each behavior change has a test (anonymous 401, wrong password/code, other user's content). Name the missing ones.
5. Run `cd Backend; ./gradlew test --no-daemon` (Windows: `.\gradlew.bat`). If `Frontend/` changed, also run `cd Frontend; npm run build`. Report pass/fail.

Output: findings ranked by severity (file:line, failure scenario, fix), missing tests, test/build result. Keep it short. Do not edit files.
