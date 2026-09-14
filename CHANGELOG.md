# Changelog

## 1.0.0

- Promoted the Maven/plugin artifact from `1.0.0-rc6-stage6` to the official `1.0.0` version without changing gameplay, commands, configuration behavior, GUIs, database behavior, or public features.
- Added a configurable RelicPrison startup presentation that reports live environment, storage, content counts, integration state, READY state, and monotonic startup duration only after asynchronous initialization completes.
- Replaced deprecated Paper/Bukkit production API usage with supported Paper 1.21.10 and Adventure APIs; retained only narrowly scoped compatibility overrides required by Paper test interfaces.
- Configured Surefire and Failsafe with Java 25 native access for SQLite tests and added a test-scope SLF4J no-op provider.
- Updated supported GitHub Actions and the Maven Wrapper, and synchronized current release, verification, limitation, and test-report documentation.
- Verified the official artifact with the current automated suite; real Paper, integration, client, recovery, and multi-server staging items remain documented rather than claimed as complete.

## 1.0.0-rc6-stage6

- Redesigned all player and staff command output with centralized RelicPrison/Gang prefixes, semantic colors, descriptive success/error text, structured status panels, useful usage blocks, and consistent value highlighting.
- Added categorized, paginated help for `/prison`, `/gang`, `/booster`, `/relicmine`, `/relicgang`, `/relicrank`, `/relicprestige`, and `/relicprison` without changing command execution behavior.
- Added behavioral command-presentation coverage and a guardrail preventing command handlers from bypassing `MessageService`.
- Redesigned every RelicPrison-created inventory with a shared premium visual language, meaningful materials, bold state colors, concise structured lore, semantic glow, contextual framing, and predictable navigation.
- Added reusable GUI item, theme, visual-state, and layout utilities plus configurable titles, filler materials, navigation materials, and interaction sounds with polished defaults.
- Added state-aware mine, progression, prestige, selling, booster, statistics, leaderboard, gang, and administration views, including player heads, online/offline status, affordability, locked/current/completed states, and contextual editor icons.
- Added dedicated red-framed confirmation inventories for rank deletion, gang kick, ownership transfer, gang disband, admin deletion, reset changes, and composition normalization.
- Added behavioral GUI tests for material selection, locked/unlocked states, semantic glow, colored structured text, pagination/navigation, confirmation placement, and gang mission/upgrade states.
- Preserved all gameplay behavior and the packaged 33-mine configuration; schema version remains 17.

## 1.0.0-rc6-stage5

- Added the native prison-focused Gangs module with database-authoritative identity, membership, invitations, ranks, rank permissions, ownership transfer, disband safety, and cross-server constraints.
- Added atomic gang bank transactions, daily withdrawal accounting, configurable upgrades, gang progression, committed-outcome contributions, missions, gang-scoped boosters, leaderboards, frozen seasons, and reward-ledger recovery.
- Added `/gang`, `/gc`, and `/relicgang`, gang GUIs and custom-rank editor flows, PlaceholderAPI values, staff audit integration, reconnect refresh, and a provider-neutral Factions migration preview/confirm interface.
- Added schema migration 17 and behavioral SQLite tests covering membership, concurrency, bank spending, ranks, upgrades, progression, missions, boosters, leaderboards, seasons, persistence, audit history, placeholders, and migration.

## 1.0.0-rc6-stage4

- Completed and hardened the rank, prestige, sell-price, booster, Block Event, reset-settings, and mine-composition GUI editors.
- Wired rank/prestige metadata into runtime progression, display, permission, requirement, multiplier, effect, and mine-access paths.
- Added real persistent active/scheduled booster management with atomic scope/target changes, cancellation, disable/enable, remaining duration, startup reload, and database reconnect reload.
- Added custom sell/composition/Block Event registry validation on the Paper server thread while retaining asynchronous file work.
- Added reset countdown, retry, recount, evacuation destination, and safety settings to runtime reset configuration.
- Added runtime ItemsAdder composition fallback metadata and reject unsupported metadata instead of silently ignoring it.
- Hardened atomic file replacement to preserve rollback copies after failed runtime reloads.
- Expanded behavioral coverage to 156 tests for editor operations, stale conflicts, input lifecycle, validation, thread marshalling, audit callbacks, rollback, pagination, and booster persistence.

## Unreleased backend recovery work

- Added replay-safe absolute targets for bulk Vault deposits and mining XP; interrupted bulk money/XP components
  return to `RETRY_READY` on startup and database reconnect and apply only their remaining target delta.
- Added explicit actual-delivery and recovery-decision production models, deterministic per-item/per-entity bulk
  component tags, and behavioral tests for mixed routes, partial overflow, failed AutoBlock, rollback direction,
  repeated recovery, reconnect recovery, and SQL crashes after progression/statistics updates.
- Corrected the bulk inventory AutoBlock gate so conversion success, rather than the configured flag alone,
  determines the committed AutoBlock dimension.
- Added schema migration 15 with frozen bulk committed-result payloads and a unique bulk commit ledger.
- Bulk AutoSell money and mining XP now use deterministic reward components; player profile progression,
  mining dimensions, sold-item totals, and money-earned totals apply in one idempotent SQL transaction.
- Bulk inventory rewards are transaction-tagged, dropped entities remain transaction-tagged, and
  `REWARD_DELIVERING` is persisted before either route mutates Bukkit state.
- AutoPickup, AutoSell, AutoBlock, and fallback-drop statistics now derive from per-block delivered quantities;
  mixed sold/picked output uses AutoSell as its single primary delivery route.
- Added schema migration 14 for bulk reward item entity UUIDs and complete frozen leaderboard reward plans.
- Bulk mining now sequences durable mutation state before reward work and removes tracked dropped items on rollback.
- Player mining dimensions now use actual committed routes and include custom block identities.
- Block Event claims freeze complete reward component plans before package creation; recovery never reads live event config.
- Leaderboard recovery recreates and verifies missing packages from frozen period plans and rejects invalid empty packages.
- Database reconnects schedule bulk, Block Event, leaderboard, and reward-ledger recovery.
- This remains unreleased because real Paper crash staging, generic console-command idempotency, and atomic
  persistence of Bukkit inventory/entity state are unresolved.

## 1.0.0-rc6-stage3

- Completed real file-backed admin GUI editor workflows for ranks, prestiges, sell prices, boosters, Block Events, reset settings, and mine composition.
- Admin GUI edits now use PDC/session-bound actions, chat input sessions with timeout/cancel, stale revision checks, full candidate validation, atomic temp-file replacement, rollback copies, service reload, and staff audit records.
- Added `AdminGuiEditorService` behavioral tests covering all seven approved editors, stale edit rejection, invalid numeric values, duplicate IDs, missing integrations, search/pagination, rollback preservation, and audit generation.
- Preserved the intentional packaged 33-mine configuration.

## 1.0.0-rc6-stage2

- Added schema migration 13 for Block Event logical claim keys, duplicate-claim counters, recovery attempts, leaderboard reward package IDs, and finalization recovery metadata.
- Block Event first-time and daily-target rewards now use deterministic logical claim keys enforced by a unique database index, preventing concurrent actions from creating duplicate packages.
- Block Event trigger creation records progress and reward-created states in the durable transaction before commit.
- Block Event startup recovery confirms existing packages or recreates recoverable packages from frozen trigger payloads and current event definitions.
- Leaderboard finalization now resumes non-finalized periods when the frozen standings snapshot matches, creates missing deterministic packages, verifies package existence, and finalizes only after verification.
- Leaderboard startup recovery finalizes incomplete periods whose packages exist and marks insufficient legacy periods as `FAILED_RECOVERABLE` for staff review.
- Added diagnostics for duplicate Block Event claims, claim recovery attempts, missing Block Event packages, missing leaderboard winner packages, and recoverable legacy leaderboard periods.
- Added repository behavior tests for logical Block Event duplicate prevention, repeated Block Event recovery, resumable leaderboard finalization, leaderboard package verification recovery, and legacy incomplete-period review.

## 1.0.0-rc6-stage1

- Added durable bulk mining transaction records with explicit states from `VALIDATED` through
  `BLOCKS_MUTATED`, reward delivery, commit, rollback, and recoverable failure.
- Bulk mining now creates the durable transaction before scheduling block mutation on the server thread.
- Bulk Custom Drop commands are frozen into reward-ledger command components instead of being executed inline.
- Bulk AutoSell uses an untracked sale path and records money/items-sold statistics only after the bulk transaction succeeds.
- Added bulk transaction startup recovery diagnostics and schema migration 12.
- Added `features.player-mining-statistics`, defaulting to `true`, independent from `features.mine-analytics`.
- Added behavioral repository tests for bulk transaction state transitions, snapshot encoding, and recovery visibility.

## 1.0.0-rc5

- Added schema migration 11 for Block Event recovery metadata and leaderboard finalization verification fields.
- Reworked bulk mining to preflight reward delivery, snapshot block data, consume blocks, and roll back consumed blocks/inventory on delivery failures before recording statistics.
- Moved Block Event trigger state and reward package creation into one database transaction for new triggers.
- Added atomic leaderboard finalization that writes the period, winner snapshots, reward ledger rows, and reward packages before marking the period finalized.
- Separated player mining statistics from the `features.mine-analytics` gate.
- Corrected the packaged daily first-place leaderboard reward to pay native money once and added duplicate-payment warnings for additive economy command patterns.
- Updated GitHub Actions to identify exactly one production `RelicPrison-*.jar` instead of hardcoding the obsolete `0.6.1-mines` artifact.
- Admin GUI labels now remain honest: deep admin configuration flows are command-assisted, not complete text/numeric GUI editors.
- The approved 33-mine packaged configuration asset is still missing from the supplied project files and remains a release blocker for public install approval.

## 1.0.0-rc4

- Added atomic reward component claiming with claim tokens, leases, expired-claim handling, and package-state aggregation.
- Completed shared reward component delivery for configured money, item, ItemsAdder item, key command, booster, command, announcement, permission, and statistic component paths.
- Moved Block Event rewards to durable trigger reservations plus shared reward packages; direct Block Event reward execution was removed.
- Moved leaderboard rewards to frozen shared reward packages and replaced read-then-insert reward ledger reservation with atomic insert-or-ignore/upsert behavior.
- Added crash-aware progression refund intent, claim, confirmed, failed, and staff-review persistence.
- Moved mining event mutation out of `MONITOR`; `MONITOR` now only cleans stale preparation contexts.
- Reordered bulk mining so rewards/statistics/events are calculated from consumed blocks, not merely provider-observed blocks.
- Added bounded inactive statistics-cache eviction with dirty-entry protection and diagnostics counters.
- Added schema migration 10 and behavioral repository tests for reward claims, Block Event trigger uniqueness, leaderboard package handoff, and refund claim races.
- `mvn clean verify` passes with 93 unit tests and 1 integration test. Real Paper and integration staging remain pending.

## 1.0.0-rc3

- Split normal mining into prepare and final commit stages so canceled block breaks do not receive rewards.
- Added a conservative AdvancedEnchantments reflective bridge that submits exposed affected block collections to the real bulk mining pipeline once per AE effect event.
- Hardened progression withdrawal states with explicit Vault ambiguity handling and staff-review recovery boundaries.
- Added persistent reward package/component tables and wired progression rewards into durable reward packages.
- Added Block Event trigger reservation rows and corrected chance-per-block probability for large bulk operations.
- Switched mining statistics reads to the live-total model to avoid temporary pending double-counting.
- Hardened backup creation and restore against symlinks and partial restore application with a rollback journal.
- Relabeled incomplete admin “editors” as command launchers and documented remaining manual staging requirements.
- Added required architecture/current-state/testing/stage documentation.

## 0.6.1-mines

- Completed the corrective Phase 1/2 pass for crash-recoverable mine copy/move operations, FAWE/WorldEdit support, reload rollback, and reconnect-safe profile loading.
- Added durable structure-operation recovery commands and region locks.
- Prevented AdvancedEnchantments listener duplication across reloads.
- Removed bundled JDK, build output, and IDE files from the delivered source archive.
- See `CHANGELOG-0.6.1.md`, `docs/PHASE-1-2-CORRECTION.md`, and `BUILD-REPORT.txt`.

## 0.6.0-mines

- Completed the Phase 1/2 implementation pass for build hardening, player loading state, mine management, and reset engine work.
- Added provider-qualified mine compositions, metadata, structure copy/move fallback, recounts, persistent reset failures, retry command, per-world reset limits, ItemsAdder reset budget, and combat-tag teleport restriction fallback.
- See `CHANGELOG-0.6.0.md` and `BUILD-REPORT.txt`.

## 0.4.2-stage18-balance-api-hotfix2

- Added `%relicprison_balance%` using RelicPrison currency formatting.
- Added `%relicprison_balance_short%` using configured abbreviations.
- Added `%relicprison_balance_raw%` without a currency symbol or grouping.
- Added `NumberFormatter.plain(...)` and `NumberFormatter.abbreviatedCurrency(...)` to the public formatting API.
- Added formatted Vault balance reads for online PlaceholderAPI contexts such as scoreboards and tab lists.

## 0.4.1-stage18-paper-events-hotfix1

- Fixed Paper 1.21.10 linkage for `EntityDamageEvent#getEntity()`.
- Fixed Paper 1.21.10 linkage for `InventoryClickEvent#getWhoClicked()`.
- Fixed the admin mine GUI `Player#openInventory(Inventory)` return descriptor.
- Added bytecode regression checks for all three runtime-sensitive signatures.

## 0.4.0-stage18

- Added ItemsAdder 4.0.16 custom item/block integration.
- Added custom item selling and custom block drop catalogs.
- Added PlaceholderAPI 2.11.6 internal expansion.
- Added complete player-facing prison menus and leaderboards.
- Added buffered player, mine, and playtime statistics with schema version 4.
- Added automatic backups, restore requests, validation, diagnostics, repair, and player export/import.
- Preserved all Stage 1-14 progression, mine, Vault, LuckPerms 5.4.164, and AdvancedEnchantments 9.22.9 behavior.
