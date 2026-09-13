# RelicPrison Architecture

## Services
- `RelicPrisonPlugin` wires lifecycle, configuration, repositories, services, listeners, commands, and optional integrations.
- Profile, progression, reward, statistics, leaderboard, backup, audit, diagnostics, reset, mine, GUI, and integration services are separate subsystem boundaries.

## Mining Flow
- `MiningListener` prepares normal breaks early without rewards, commits at `HIGHEST` if the event remains uncanceled, and uses `MONITOR` only for observation/cleanup.
- `MiningServiceImpl` owns normalized normal and bulk processing, including drops, custom drops, AutoSell, AutoPickup, AutoSmelt, AutoBlock, XP, statistics, Block Events, and block removal where RelicPrison consumes the block. RC6 Stage 1 bulk operations create a durable transaction row, snapshot block data, schedule mutation only after the transaction exists, deliver command Custom Drops through the reward ledger, and roll back consumed blocks/inventory on synchronous delivery failure.

## Bulk Mining Transactions

- `rp_bulk_mining_transactions` stores one bounded row per bulk operation with transaction ID, player UUID, source, mine ID, state, block count, consumed count, reward package ID, encoded block snapshots, frozen reward payload, recovery attempts, and failure summary.
- The state flow is `VALIDATED -> BLOCKS_MUTATED -> REWARD_DELIVERING -> COMMITTED`, with failure routes through `ROLLING_BACK`, `ROLLED_BACK`, `FAILED_RECOVERABLE`, or `FAILED_PERMANENT`.
- Startup recovery inspects incomplete records. Unmutated transactions are closed as rolled back; partial or absent block states are moved to recoverable staff review rather than duplicated.
- AdvancedEnchantments reflection remains in `AdvancedEnchantmentsIntegration`; when AE exposes affected blocks, the bridge submits them to `processBulk()` once.

## Progression State Machine
- Progression transactions persist `CREATED`, `VALIDATED`, `WITHDRAWAL_INTENT_RECORDED`, `WITHDRAWAL_IN_PROGRESS`, `WITHDRAWAL_CONFIRMED`, `WITHDRAWAL_AMBIGUOUS`, `PROFILE_UPDATE_PENDING`, `PROFILE_SAVED`, `PERMISSIONS_UPDATE_PENDING`, `PERMISSIONS_UPDATED`, `REWARDS_PENDING`, `COMPLETED`, `COMPENSATION_PENDING`, `FAILED`, and `STAFF_REVIEW` equivalents.
- Generic Vault ambiguity is represented explicitly and stops automatic recovery.

## Reward Ledger
- `rp_reward_packages` stores frozen package metadata.
- `rp_reward_components` stores immutable component payloads, state, due time, attempts, idempotency keys, claim tokens, claim owner, claim timestamps, next-attempt timestamps, and delivered timestamps.
- Scheduler, player-join, recovery, and staff retry paths claim components with one compare-and-set database update before any external side effect.
- Startup moves claimed or running components to `AMBIGUOUS` for staff review because generic external APIs cannot prove whether a process died during the side effect.

## Block Events
- `rp_block_event_triggers` reserves trigger identities before reward delivery.
- RC6 Stage 2 Block Events store deterministic logical claim keys. First-time claims use `(event_id, player_uuid, first)`, daily claims use `(event_id, player_uuid, daily_period)`, and repeatable events use the approved occurrence identity. The `rp_block_event_logical_claim` unique index is the exactly-once authority; in-memory state is only a cache.
- Block Event transactions use `RESERVED`, `PROGRESS_RECORDED`, `REWARD_CREATED`, `COMMITTED`, `FAILED_RECOVERABLE`, and `CANCELLED`. New triggers persist progress updates, frozen package rows, and components in one database transaction before `COMMITTED`.

## Leaderboard Finalization
- RC6 Stage 2 finalization writes or resumes the period row, winner snapshots, reward ledger rows, reward packages, and reward components in one repository transaction, then marks the period `FINALIZED` only after deterministic package IDs are verified.
- Leaderboard periods use `RESERVED`, `SNAPSHOT_CREATED`, `REWARDS_CREATING`, `REWARDS_VERIFIED`, `FINALIZED`, `FAILED_RECOVERABLE`, and `CANCELLED`. Recovery finalizes periods with complete packages and marks insufficient legacy periods for manual review.
- External reward delivery starts after the transaction commits through the shared reward ledger.
- Chance-per-block uses `1 - (1 - p)^n` across the complete accepted operation.

## Leaderboards
- Leaderboard reads are asynchronous and cached.
- Period reservation uses atomic insert-or-ignore semantics.
- Reward rows remain protected by unique constraints; RC4 freezes leaderboard reward packages and hands delivery to the shared reward ledger.

## Statistics

- RC6 Stage 1 separates approved per-player mining statistics from rejected mine-level analytics with independent gates: `features.player-mining-statistics` and `features.mine-analytics`.
- Player mining dimensions by material, source, mine, AutoSell, and AutoPickup use the player-mining-statistics gate and remain available while mine analytics are disabled.
- Mine aggregate totals use the mine-analytics gate and are not required for player placeholders or progression statistics.
- Mining statistics use live-total model B: in-memory live totals include persisted plus pending deltas, and pending maps are only the persistence buffer.
- Startup no longer loads all historical mining-stat rows; player-period rows load on active player demand and leaderboard queries read asynchronously.
- Inactive player statistic caches are bounded, UUID-keyed, expire after a safe flush, and are not evicted while dirty.

## Backup and Restore
- Backup creation rejects symlinked sources and validates real-path containment.
- Restore verifies archive integrity, extracts to staging, writes a safety backup, rejects symlink destination paths, journals overwritten files, and rolls back on apply failure.

## Admin GUI Editors

- RC6 Stage 3 adds `AdminGuiEditorService` for rank, prestige, sell-price, booster, Block Event, reset-settings, and mine-composition editing.
- Admin editor clicks use PDC action identifiers and normal GUI session/generation validation. Text and numeric values are collected through per-staff chat input sessions that expire, support `cancel`, and are removed on disconnect.
- Saves use optimistic locking by source-file SHA-256 revision. Stale saves are rejected and the GUI reloads current values instead of overwriting newer configuration.
- Each save mutates an in-memory candidate, validates the complete candidate through the production loaders, writes a temp file, flushes it, atomically replaces where supported, keeps a rollback copy, reloads the affected service, rolls back on reload failure, and records staff audit metadata.

## Packaged Mine Resource

- Fresh installs copy the bundled `mines.yml` only when no live `plugins/RelicPrison/mines.yml` exists; upgrades never overwrite an existing mine file.
- `PackagedMineResourceStatus` validates the bundled resource and reports the current external release blocker: the exact approved corrected 33-mine asset is absent from the supplied repository. RelicPrison does not fabricate mine IDs, coordinates, worlds, compositions, reset settings, or progression associations.

## Thread Boundaries
- Database work uses `DatabaseManager`.
- Bukkit operations remain on the server thread.
- Backup, verification, and diagnostics use bounded asynchronous snapshots where available.
