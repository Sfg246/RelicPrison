# RelicPrison Master Specification

RelicPrison is a Paper-only prison plugin for mines, progression, selling, boosters, statistics, PlaceholderAPI, GUIs, leaderboards, diagnostics, auditing, and backups.

## Core Guarantees
- Player, mine, profile, progression, reward, and backup data must be preserved through backward-compatible migrations.
- Reward-producing operations must have duplicate protection or durable recovery state.
- Generic Vault withdrawals can become ambiguous; ambiguous outcomes require staff review instead of automatic retry.
- Mining rewards must only commit after the final supported block-break cancellation decision.
- Normal and bulk mining share the same lower-level processing rules for drops, AutoSell, AutoPickup, AutoSmelt, AutoBlock, XP, statistics, Custom Drops, and Block Events.
- Bulk mining must create a durable transaction before block mutation, snapshot affected block state, preflight reward routes, route command rewards through durable reward components, and roll back captured block/inventory state on synchronous delivery failure.
- Bulk AutoSell must not finalize money-earned or items-sold statistics until the transaction succeeds.
- Player mining statistics are controlled independently from mine-level analytics toggles.
- Block Event triggers and leaderboard finalization must not mark work complete before deterministic reward packages exist.
- First-time and daily Block Event rewards must use deterministic logical claim keys enforced by database uniqueness, not random trigger IDs.
- Leaderboard finalization must be resumable from non-finalized periods and must preserve frozen standings snapshots across retries.
- Placeholder evaluation is read-only and must use cached snapshots or already-loaded state.
- Admin GUI editors must call the same configuration validation/reload paths as commands, save atomically with rollback, reject stale edits, and audit staff actions.
- Leaderboard reads use asynchronous refreshes or cached snapshots.
- Backup verification must not modify live data; restore must use staging, validation, a safety backup, and rollback.
- Fresh-install mine defaults must use the exact approved corrected 33-mine asset when supplied. Missing approved mine data must be reported as a release blocker rather than fabricated.

## Integration Boundaries
- Vault, LuckPerms, PlaceholderAPI, ItemsAdder, AdvancedEnchantments, WorldEdit/FAWE, WorldGuard, Geyser, and Floodgate are optional runtime integrations.
- Integration-specific reflection and event classes stay in integration services.
- Core services must not require optional integration classes when plugins are absent.

## Manual Verification Boundary
Automated tests cover repository behavior, configuration parsing, guardrail scans, and limited Paper API compilation. Real Paper 1.21.10 behavior, AdvancedEnchantments, ItemsAdder, Java client, Bedrock client, economy failure, LuckPerms failure, and restart/crash staging must be performed on a staging server before production release.
