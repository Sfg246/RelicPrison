# RelicPrison Decisions

## Vault Ambiguity
Generic Vault economies do not provide a universal idempotency key or transaction-history API. If the JVM stops during a withdrawal call, RelicPrison cannot prove whether money moved. The transaction therefore enters staff review instead of retrying and risking a double charge.

## Component Reward Ledger
Package-level reward state is insufficient because a package can partially deliver. Frozen components allow retrying incomplete commands, money, announcements, and future reward types without replaying delivered components.

## Mining Commit Order
Normal mining prepares early, commits at `HIGHEST`, and uses `MONITOR` only for cleanup because Bukkit `MONITOR` listeners must not mutate event outcomes. Prepared state is immutable and does not grant rewards. Canceled or changed blocks are abandoned.

## AdvancedEnchantments Bulk Capture
The AE bridge remains optional and reflective. When AE exposes an affected block collection, RelicPrison deduplicates and submits the collection to `processBulk()` once. Real AE 9.22.9 staging remains required.

## Statistics Cache Model
RelicPrison uses live-total model B. Reads return live totals directly; pending counters are only for batched persistence and are restored after failed flushes.

## Restore Rollback
Directory-swap portability is uneven on hosted servers. The restore path uses staged extraction plus a transaction journal of overwritten files and restores prior files automatically on apply failure.

## Admin GUIs
RC6 Stage 3 implements the approved admin submenus as real file-backed editors for composition, reset settings, ranks, prestiges, sell prices, boosters, and Block Events. They use PDC actions, per-staff chat input sessions, optimistic locking, full-candidate validation, atomic replacement with rollback, service reload, and staff audit records.

## Known Integration Limits
Real ItemsAdder, AdvancedEnchantments, Vault economy failure, LuckPerms failure, Java client, and Bedrock client behavior cannot be proven by unit tests in this repository.

## RC4 Correction Decisions

- Reward component delivery uses database claim tokens instead of in-memory locks because scheduler, join, startup, and staff retry paths can run independently.
- Expired or interrupted claims become `AMBIGUOUS` rather than automatic retry when an external side effect may have happened without durable confirmation.
- Block Event and leaderboard reward paths now create frozen reward components; retries for created packages do not reread changed reward configuration.
- Bulk mining now consumes blocks before reward/stat/event accounting. A durable bulk operation journal remains a known limitation for crash recovery between consumption and reward package creation.

## RC5 Reliability Decisions
- Bulk mining now preflights inventory/AutoBlock/AutoSell/Vault availability before mutation and rolls back captured `BlockData` plus inventory if a synchronous delivery failure occurs after mutation begins.
- `features.mine-analytics` gates mine-level analytics only; player mining statistics for placeholders and leaderboards remain independent.
- Block Event state updates and reward package creation are one durable write for new triggers to remove the consumed-without-reward crash window.
- Leaderboard finalization now writes winners and packages before setting the period to `FINALIZED`.
- The supplied repository lacks the approved corrected 33-mine coordinates/world asset, so RC5 keeps `mines.yml` empty instead of inventing data.

## RC6 Stage 1 Bulk Mining Decisions

- Bulk mutation is scheduled only after the database worker creates `rp_bulk_mining_transactions`; the main thread does not wait on the database.
- Bulk Custom Drop commands are treated as external, irreversible rewards and are represented as frozen reward-ledger components instead of inline command dispatch.
- Bulk AutoSell deposits are executed without immediate profile/statistic mutation; money-earned, items-sold, and sell-summary totals are recorded only after the bulk transaction reaches the success path.
- Recovery avoids guessing external side effects after a crash. If blocks are absent and reward/statistic reconciliation is not provable, the transaction enters `FAILED_RECOVERABLE` for staff review instead of duplicating rewards.

## RC6 Stage 1 Statistics Decision

- `features.player-mining-statistics` is a default-on approved feature. `features.mine-analytics` remains a separate default-off rejected analytics feature. The two gates are intentionally independent so disabling mine analytics cannot disable player progression/stat placeholders.
- Statistics cache eviction is flush-driven and skips dirty entries; leaderboards use independent snapshots so disconnected player live caches can expire.
- Restore uses a rollback journal for file replacement. Complete isolated semantic validation of all cross-file references remains a staging/release blocker before final `1.0.0`.

## RC6 Stage 2 Recovery Decisions

- Block Event first-time and daily rewards cannot use random trigger IDs as the exactly-once key. They now derive a deterministic logical claim key and rely on a unique database index so concurrent mining actions, reloads, or server instances converge on one entitlement.
- Block Event in-memory state remains a performance cache only. The database trigger row and logical claim index are authoritative; duplicate logical attempts increment `duplicate_count` for diagnostics.
- Leaderboard finalization no longer treats existing non-finalized periods as terminal. If the existing frozen standings snapshot matches the retry, the repository resumes winner/package creation and finalizes after package verification.
- Legacy leaderboard periods without enough package data are marked `FAILED_RECOVERABLE` rather than silently finalized or discarded. This preserves evidence and exposes the issue to diagnostics/staff review.

## RC6 Stage 3 Mine Asset Decision

- The exact approved corrected 33-mine fresh-install configuration was not present in the supplied repository, archives, documentation, or packaged resources. RC6 Stage 3 therefore detects and reports the missing asset instead of fabricating mine IDs, coordinates, worlds, compositions, reset settings, or progression associations.
