# Stages 15-18 Current Status

This file supersedes older completion claims. Use `docs/stages/stage-15.md` through `docs/stages/stage-18.md` and `docs/CURRENT_STATE.md` for current status.

## Implemented

- ItemsAdder-aware custom item/block configuration paths remain optional.
- PlaceholderAPI and player-facing menus are implemented.
- RC6 Stage 3 admin GUI editors are implemented for ranks, prestiges, sell prices, boosters, Block Events, reset settings, and mine composition.
- Statistics and asynchronous/cached leaderboards are implemented.
- Diagnostics, diagnostic exports, auditing, backups, verification, and staged restore are implemented.

## Corrected Through `1.0.0-rc6-stage3`

- Admin submenus now use real editor workflows with safe chat input, optimistic locking, validation, atomic replacement, rollback, reload, and audit records.
- Leaderboard period reservation uses atomic insert-ignore semantics.
- Mining statistics no longer add pending deltas twice.
- Backup restore rejects symlink destinations and rolls back partial apply failures.
- Block Event trigger state and reward package creation are atomic for new triggers.
- Leaderboard finalization now writes winner snapshots, frozen rewards, reward packages, and components
  in one database transaction before marking the period finalized.
- Player mining statistics remain enabled when mine analytics are disabled.
- Default leaderboard rewards no longer include a duplicate economy command beside native money.

## Not Fully Complete

- The approved corrected 33-mine fresh-install `mines.yml` asset is not present in the supplied
  source tree; the packaged resource remains intentionally empty rather than fabricated.
- Bulk mining now records durable transaction snapshots and routes bulk command rewards through the reward ledger, but real crash-window and integration staging are still required for public release approval.

## Required Before `1.0.0`

Run and record the full staging checklist in `STAGING-CHECKLIST-1.0.0-rc6-stage3.md`.

## RC6 Stage 3 Correction Update

- RC6 Stage 3 supersedes the Stage 2 checklist/report filenames with `*-1.0.0-rc6-stage3.*`.
- Reward component delivery now uses atomic claim tokens and leases.
- Block Event and leaderboard reward delivery now hand off to shared reward packages.
- Bulk mining uses one durable transaction row per operation and does not commit player mining statistics until mutation and delivery succeed.
- Player mining statistics are gated independently from mine analytics.
- Block Event first-time and daily entitlements now use deterministic logical claim keys with a unique database constraint.
- Leaderboard non-finalized periods now resume package verification/finalization instead of remaining stranded.
- Admin GUI editors are implemented; real Paper Java/Bedrock workflow staging remains required.
- Real Paper/integration staging remains required before final `1.0.0`.

