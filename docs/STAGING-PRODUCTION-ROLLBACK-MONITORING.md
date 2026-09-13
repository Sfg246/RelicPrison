# Staging, Production Deployment, Rollback, and Monitoring

Manual tests in this document must be performed on a real Paper server. Do not mark them passed from a local Maven build.

## Full Staging Checklist

Accounts and permissions:

- Rank A account.
- Middle-rank account.
- Rank Z account.
- Every configured prestige.
- Donor permissions.
- Staff permissions.
- Java client.
- Bedrock client through Geyser.

Core workflows:

- Join, profile load, disconnect, and reconnect.
- Rankup and Rankup Max confirmation.
- Prestige confirmation.
- Selling through `/sellall`, `/sellhand`, `/sellvalue`, and GUI display.
- Booster item use, command activation, server booster status, personal booster status, expiry, and duplicate activation rejection.
- Mine teleport and access restrictions.
- Vanilla mining and bulk mining.
- AutoSell, AutoPickup, AutoSmelt, AutoBlock.
- Custom Drops.
- Block Events.
- PlaceholderAPI scoreboard refresh.
- Player GUIs.
- Admin GUIs.
- Leaderboards, player position, pages, and PlaceholderAPI leaderboard values.
- Period rewards, offline rewards, reward retry, and reward history.

Mine and reset coverage:

- Every mine.
- Manual reset, timed reset, percentage reset, warning reset, cancellation, and retry.
- Reset order variants configured in staging.
- Vanilla reset blocks.
- ItemsAdder reset blocks.
- Mine deletion during reset must be refused.
- Mine movement/copy during reset must be refused.
- World unload during reset must fail safely and persist retryable state.

Integration coverage:

- AdvancedEnchantments Drill.
- AdvancedEnchantments Trench.
- AdvancedEnchantments Seismic Drill.
- AdvancedEnchantments fortune and durability interactions.
- AdvancedEnchantments outage.
- ItemsAdder block placement.
- ItemsAdder custom drops.
- ItemsAdder outage.
- PlaceholderAPI outage.
- Vault/economy failure.
- LuckPerms failure.
- Database outage and latency.
- Server shutdown with pending writes.
- Reward finalization during restart.

Backup and restore:

- `/rp backup create full`
- `/rp backup info <id>`
- `/rp backup verify <id>`
- Corrupt backup verification rejects the archive.
- Path-traversal backup verification rejects the archive.
- `/rp backup restore <id> confirm` creates a safety backup and restore plan.
- Restart after restore validates plugin data.
- `/rp backup delete <id> confirm` refuses active restore sources and audits deletion.

Manual-only items:

- Real Java client UX.
- Real Bedrock/Geyser UX.
- Real AdvancedEnchantments behavior.
- Real ItemsAdder behavior.
- Real database outage/latency.
- Real restart/crash windows.
- Real production backup/restore drill.

## Production Deployment Checklist

Before deployment:

- Freeze RelicPrison configuration changes.
- Take a full server backup.
- Run `/rp backup create full`.
- Run `/rp backup verify <id>` on the RelicPrison backup.
- Export LuckPerms data.
- Preserve the previous working RelicPrison JAR.
- Verify checksums for the new JAR, old JAR, full server backup, RelicPrison backup, and LuckPerms export.
- Confirm all required migrations are documented and backup files are readable.

Private-access rollout:

- Start the server with private access.
- Run `/rp diagnose detail`.
- Run `/rp validate`.
- Test sample Rank A, middle-rank, Rank Z, and prestige accounts.
- Test rankup, Rankup Max, prestige, selling, reset, Java client, Bedrock client, and integrations.
- Test leaderboards and reward history.
- Test backup verification.

Public-access gate:

- Open public access only after all critical checks pass.
- Keep staff online during the initial public window.
- Monitor diagnostics every few minutes during the first reset/reward period.

## Rollback Process

Rollback is required when:

- Data corruption is observed.
- Rankup/prestige transactions enter unrecoverable review unexpectedly.
- Backup verification or restore verification fails during rollout.
- Economy, LuckPerms, mining, resets, or profile saves fail broadly.
- Performance regressions threaten TPS/MSPT or database queue stability.

How to stop writes safely:

- Put the server in private/maintenance mode.
- Stop new player joins.
- Wait for `/rp diagnose` to show low or zero database queue when possible.
- Stop the server cleanly so dirty profiles, statistics, resets, boosters, and pending writes flush.

Restore previous JAR:

- Move the new JAR out of the plugins directory.
- Restore the previous working RelicPrison JAR.
- Do not delete new backups or diagnostic exports.

Restore plugin data:

- Prefer `/rp backup restore <id> confirm` followed by a full restart for local SQLite/config restores.
- For full server rollback, restore from the server backup with the server offline.
- Keep the safety backup created by restore.

Handle database schema changes:

- RelicPrison migrations are backward-compatible forward migrations.
- Do not manually downgrade schema tables unless a tested SQL rollback exists.
- If returning to a binary that cannot read the newer schema, restore the pre-upgrade database backup instead.
- For MySQL, stop writes and restore through database administration tools; RelicPrison does not automatically restore remote MySQL data.

Restore LuckPerms when necessary:

- Restore the LuckPerms export if groups or inheritance were damaged.
- Start privately and run progression repair on affected players.
- Check `/rp diagnose detail` for LuckPerms health and incomplete progression transactions.

Inspect incomplete transactions after rollback:

- `/rp progression list`
- `/rp progression info <transaction-id>`
- `/rp progression retry <transaction-id>` only when the transaction is automatically recoverable.
- Move unrecoverable external inconsistencies to staff review and document manual compensation.

Confirm previous version health:

- Run `/rp diagnose detail`.
- Run `/rp validate`.
- Test profile load, rankup/prestige repair, mining, selling, reset, and LuckPerms group consistency.
- Keep the rollback window private until health checks pass.

## Post-Release Monitoring

Use `/rp diagnose` for concise status and `/rp diagnose detail` for sensitive staff-only details.

Monitor:

- Reset durations and slow reset slices.
- Reset allocation-related behavior through profiler/JFR when staging.
- ItemsAdder placement time.
- Database queue depth and connection-pool health.
- Failed saves and dirty profiles.
- Economy failures.
- Incomplete progression transactions.
- LuckPerms synchronization.
- Booster expiration.
- Placeholder load and slow-placeholder diagnostics.
- AdvancedEnchantments processing.
- Statistics flushing and pending buckets.
- Leaderboard refresh and cached snapshots.
- Daily, weekly, monthly, and seasonal rollovers.
- Reward delivery, pending offline rewards, and staff-review rewards.
- Backup creation and verification.
- Failed commands.
- Memory growth.
- Bukkit task backlog and repeated listeners after reload.

Useful commands:

- `/rp status`
- `/rp diagnose`
- `/rp diagnose detail`
- `/rp validate`
- `/rp progression list`
- `/rp rewards pending`
- `/rp rewards history 50`
- `/rp backup list`
- `/rp backup verify <id>`
- `/rp audit 1 action=backup_verify`

RelicProfiler/JFR staging markers:

- `metric.mining.normal.*`
- `metric.mining.bulk.*`
- `metric.reset.slice.*`
- `metric.reset.itemsadder-placement.*`

Known limitations:

- No external metrics platform is added.
- Some performance characteristics require Paper/JFR/RelicProfiler staging; Maven tests only verify source-level constraints and unit behavior.
- Bedrock/Geyser, AdvancedEnchantments, ItemsAdder, real restart, and real outage tests remain manual staging requirements.
