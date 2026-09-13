# Current State

## Implemented
- Paper 1.21.10 API build using Java 25 and Maven Wrapper.
- Mining prepare/commit split for normal block breaks; event mutation now occurs at `HIGHEST`, while `MONITOR` only cleans up.
- Shared normal/bulk mining service with deduplication, operation limits, consumed-only accounting, and RC6 Stage 1 durable transaction records for bulk mutation/reward recovery.
- Bulk committed results freeze actual per-block delivery routes. Partial pickup overflow is not AutoPickup,
  unsellable output is not AutoSell, partial sales count only sold items, and AutoBlock counts only successful conversion.
- Bulk XP and Vault reward components freeze absolute player targets and replay only the missing delta after startup
  or database reconnect; progression and mining/sell statistics remain protected by the unique SQL commit ledger.
- Explicit progression withdrawal intent, in-progress, confirmed, ambiguous, and staff-review states.
- Crash-aware progression refund intent, claim, confirmed, failed, and staff-review state persistence.
- Persistent reward package/component tables with atomic component claiming, claim leases, retry scheduling, and package aggregation.
- Block Event logical claims freeze complete component plans before two-phase package creation and recover without live configuration.
- Leaderboard periods freeze standings and reward plans, reconstruct missing packages, and reject invalid empty packages.
- Seven approved admin GUI editors are implemented with GUI entry selection, search/pagination, create/edit/toggle/delete where supported, chat input sessions, stale revision rejection, atomic file replacement, retained rollback copies, main-thread reload, and staff audit records.
- Runtime-managed boosters persist active, scheduled, and disabled instances, support atomic scope/target changes, and reload after startup or database reconnect.
- Statistics live-total model without pending double-counting plus bounded inactive-player cache eviction.
- Backup symlink rejection, verification, safety backup, staged extraction, restore journal, and rollback.
- Diagnostic export redaction tests and backup verification tests.
- Native prison gangs with stable database IDs, unique names/tags, persistent membership and invitations, default/custom ranks, rank permissions, atomic ownership transfer, safe disband, bank history and spending controls, upgrades, committed-outcome progression, missions, gang-scoped boosters, chat, GUIs, leaderboards, frozen seasons, placeholders, and staff/gang audit records.
- Gang contributions are submitted only after committed RelicPrison mining, selling, rankup, prestige, Block Event, and leaderboard finalization outcomes; deterministic operation claims suppress replay.
- Gang state reloads after database reconnect and refreshes online-player caches for cross-server changes.
- Every RelicPrison inventory now uses a shared visual system with meaningful feature materials, state-aware color/glow, structured lore, contextual filler, standardized navigation, paginated growing collections, and protected destructive confirmations.
- GUI titles, theme materials, filler accents, and click/success/denied/teleport/delete sounds have polished configurable defaults under `guis/`.
- Every registered command handler routes player-facing text through `MessageService`; player and staff help menus are categorized and paginated, status/list output uses shared sections and fields, and gang output uses a dedicated configurable prefix.

## Automated Verification
- Repository tests cover progression transaction duplicate protection, progression refund claim races, reward ledger atomic claims and lease recovery, leaderboard atomic finalization rows/packages, leaderboard recovery/finalization resumption, Block Event atomic trigger/package creation, Block Event logical duplicate-claim prevention, bulk mining transaction states/snapshots/recovery visibility, Block Event chance math, backup traversal/absolute path rejection, redaction, and configuration parsing.
- The latest Stage 6 verification count is recorded in `TEST-REPORT-1.0.0-rc6-stage6.md` after the final clean test/package pass.
- Static guardrail tests remain as secondary checks and are not treated as completion proof.

## Manual Verification Pending
- Real Paper 1.21.10 server startup/reload.
- AdvancedEnchantments 9.22.9 Drill, Trench, Seismic Drill, durability, fortune, and outage behavior.
- ItemsAdder custom block and item behavior.
- Vault economy failure and ambiguous withdrawal reconciliation.
- LuckPerms permission update failure/repair.
- Java and Bedrock client GUI workflows through Geyser/Floodgate.
- Backup restore and rollback on a staging server.
- Two real Paper servers sharing one supported database, including simultaneous gang join, withdrawal, upgrade, ownership-transfer, mission-claim, and season-finalization attempts.
- Java and Bedrock client testing for every gang GUI, gang chat, home, invitation, rank editor, and confirmation flow.
- Java and Bedrock visual inspection of every redesigned inventory, including client resource-pack interactions and configurable sound balance.
- Java and Bedrock chat inspection of every command/help page for wrapping, hoverless readability, and color visibility with the production resource pack.

## Known Limitations
- Generic Bukkit/third-party commands cannot prove exactly-once completion because the receiver exposes no
  idempotency key or transaction participation; interrupted `RUNNING` commands remain ambiguous and are not repeated.
- Paper inventory and dropped-entity persistence is outside the SQL transaction. Deterministic tags make rollback
  cleanup repeatable for loaded state, but a hard process loss between Bukkit mutation and player/chunk save remains unproven.
- Requested real Paper crash, partial mutation, reconnect, and offline-delivery staging has not been performed.
- Restore verification performs ZIP/path/YAML/database readability checks and rollback-journal application; complete isolated cross-file semantic validation is still staging-required.
- The packaged 33-mine configuration is intentional and remains unchanged.
- Vault cannot participate in the gang-bank SQL transaction. Deposit/withdraw compensation is implemented, but hard-crash ambiguity between the external Vault commit and the database commit requires real failure staging and operator reconciliation.
- No bundled Factions-data reader is provided because no concrete Factions implementation/schema was supplied; the migration service exposes preview/confirm provider boundaries and never guesses foreign schemas.
- Ally chat is intentionally omitted from this prison-gang core.
- The requested WarpMenuPlugin reference files were not present in the supplied workspace, so direct comparison against its `config.yml`, `MenuManager`, and `ItemUtil` was not possible; the redesign follows the detailed visual specification supplied with this stage.
- No claim of production readiness is made from compilation alone.

## Recommended Version
Use `RelicPrison 1.0.0-rc6-stage6` for controlled Paper staging only. Do not promote it to final `1.0.0` until the real Paper/integration/client matrix passes.


