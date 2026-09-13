# API Thread-Safety Reference

RelicPrison separates Bukkit-thread operations from database and persistence work.

## Main Thread Only

These operations must run on the Bukkit server thread:

- Mining block processing.
- Bulk mining block mutation.
- Bukkit inventory mutation.
- Bukkit economy calls through Vault.
- LuckPerms user/group mutation callbacks that touch Bukkit state.
- Console command dispatch.
- GUI open/click handling.
- Mine reset block placement.
- ItemsAdder custom block placement/removal.

RelicPrison command, mining, reset, GUI, Custom Drops, Block Events, and reward services enforce this by dispatching Bukkit work synchronously and keeping database work asynchronous.

## Asynchronous

These operations run off the main thread through bounded executors or cached snapshots:

- Database schema migration.
- Profile load/save.
- Progression transaction record persistence.
- Statistics flush.
- Leaderboard queries and reward ledger updates.
- Audit ledger writes and paginated audit reads.
- Backup creation, verification, deletion, and diagnostic export file IO.
- Diagnostic database health checks.

## Safe Snapshot Reads

These are safe read-only access patterns:

- `/rp diagnose` reads bounded in-memory snapshots and queues database checks.
- PlaceholderAPI evaluation reads cache snapshots and never runs synchronous SQL.
- Leaderboard placeholders use cached leaderboard snapshots.
- Diagnostics report `unavailable` instead of fabricating service values.

## Idempotency Boundaries

Durable ledgers protect non-idempotent operations:

- `rp_progression_transactions` protects rankup/prestige withdrawal, profile save, LuckPerms update, and completion rewards.
- `rp_leaderboard_reward_periods` finalizes a reward period once.
- `rp_leaderboard_reward_ledger` protects individual competitive reward delivery.
- `rp_reward_delivery_log` is available for durable reward idempotency.
- `rp_staff_audit` records permanent staff actions and is append-only through plugin commands.

External command rewards are not inherently idempotent outside RelicPrison. RelicPrison inserts duplicate-protection records before dispatch and records failures through diagnostics/audit where supported.
