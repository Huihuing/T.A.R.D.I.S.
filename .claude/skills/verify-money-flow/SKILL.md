---
name: verify-money-flow
description: Verify the current branch's changes to virtual money flows (wallet, ledger, transfer, buy/sell, reward, limit order, portfolio) for duplicate execution, concurrency, negative balance, rollback and ledger gaps, then run the backend tests.
disable-model-invocation: true
context: fork
agent: ledger-reviewer
---

Verify the money-flow changes on the current branch.

1. Collect the diff: `git diff origin/main...HEAD` plus uncommitted changes (`git diff`, `git status --short`). If $ARGUMENTS names files, commits or a PR, use those instead.
2. Keep only files that change balances, ledger entries, orders, rewards, portfolios, money math or their migrations. If none, report "no money-flow changes" and stop.
3. Review them against your checklist: duplicate execution, `...ForUpdate` locking and lock order, rejection before mutation (overflow, negative, precision, `NaN`/`Infinity`), `markRollbackOnlyIfActive()` on caught errors, one ledger entry per balance change, server-side price and JWT identity.
4. Check that each behavior change has a test. Name the missing ones.
5. Run `cd Backend; ./gradlew test --no-daemon` (Windows: `.\gradlew.bat`). Report pass/fail counts; quote only failing test names and the relevant assertion lines.

Output: findings ranked by severity (file:line, failure scenario, fix), missing tests, test result. Keep it short. Do not edit files.
