# Stage 19 - Backend Recovery Closure (In Progress)

## Implemented

- Schema 14 persists bulk reward entity UUIDs and complete leaderboard reward plans.
- Bulk state persistence is sequenced before reward work; rollback removes tagged/persisted item entities.
- Player mining dimensions derive AutoSell, AutoPickup, and AutoBlock counts from committed routes.
- Block Event claims freeze complete component plans before package creation and recover without live config.
- Leaderboard recovery recreates and verifies deterministic packages from frozen period plans.
- Startup and database reconnect hooks schedule all relevant recovery services.
- Bulk money and XP components freeze absolute targets and replay only a missing delta.
- SQL fault injection proves profile progression, sell totals, mining dimensions, and analytics roll back together
  before commit and apply once on recovery.
- Per-block route outcomes are frozen from successful delivery, with one primary delivery route per block and
  AutoBlock tracked independently.

## Automated Evidence

- Java 25 full unit/repository suite: 142 tests, 0 failures, 0 errors, 0 skipped.

## Release Blockers

- Arbitrary console-command effects cannot be proven exactly-once without downstream idempotency support.
- Paper does not expose a transaction joining player-data/entity persistence to the plugin SQL commit; deterministic
  tags support repeatable loaded-state cleanup, but forced-process-loss staging is still required.
- The requested real Paper crash, partial mutation, reconnect, and offline-delivery matrix was not run.

The project version remains `1.0.0-rc6-stage3`; no Stage 4 JAR is authorized by this pass.
