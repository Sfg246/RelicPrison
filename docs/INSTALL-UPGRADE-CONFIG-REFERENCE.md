# Installation, Upgrade, Configuration, Commands, Permissions, and Placeholders

## Installation

Requirements:

- Paper `1.21.10`.
- Java `25`.
- Vault with a registered economy provider.
- LuckPerms.
- Optional: PlaceholderAPI, ItemsAdder, WorldEdit or FAWE, WorldGuard, AdvancedEnchantments, Geyser.

Install:

- Stop the server.
- Place `RelicPrison-<version>.jar` in `plugins/`.
- Keep Vault, LuckPerms, and optional integrations on tested versions.
- Start the server once to create default configuration.
- Configure `storage.yml`, `config.yml`, `mines.yml`, `ranks.yml`, `prestiges.yml`, `sell-prices.yml`, `boosters.yml`, `mining.yml`, `custom-drops.yml`, `block-events.yml`, `leaderboards.yml`, `leaderboard-rewards.yml`, and `guis/*.yml`.
- Run `/rp validate` and `/rp diagnose`.

When using the SQLite backend on Java 25, grant `sqlite-jdbc` native access when Paper starts:

```text
java --enable-native-access=ALL-UNNAMED -jar paper.jar --nogui
```

RelicPrison cannot grant native access after the server JVM has launched. MySQL-only servers do not load the SQLite native library during normal operation.

## Upgrade Notes

From `0.4.2` and intermediate versions:

- Back up the full server.
- Export LuckPerms.
- Preserve the previous RelicPrison JAR.
- Upgrade through a private-access staging startup first.
- Let RelicPrison apply database migrations automatically.
- Run `/rp validate`.
- Run `/rp diagnose detail`.
- Test rankup, prestige, selling, mining, resets, LuckPerms groups, PlaceholderAPI, GUIs, leaderboards, backups, and restore verification.

Database migration notes:

- Schema `1`: player profiles, boosters, mine runtime, migration history, backup history.
- Schema `2`: personal multipliers.
- Schema `3`: booster pause state.
- Schema `4`: player/mine statistics.
- Schema `5`: reset recount fields and reset failure records.
- Schema `6`: crash-safe progression transactions, reward delivery log, mining statistics, block-event state.
- Schema `7`: leaderboard reward periods and reward ledger.
- Schema `8`: immutable staff audit ledger.

All migrations are forward, additive, and preserve existing player data. Restore a pre-upgrade database backup if an older JAR cannot read a newer schema.

## Configuration Reference

Important files:

- `config.yml` — core feature toggles, timezone, reset engine, progression, logging, placeholder cache durations, GUI security, leaderboard refresh, and reward limits.
- `storage.yml` — SQLite/MySQL connection settings, queue capacity, retry count, save intervals, and shutdown flush timeouts.
- `mines.yml` — mine cuboids, composition, reset settings, access restrictions, metadata, and hooks.
- `ranks.yml` — configured rank order, costs, mines, LuckPerms groups, and commands.
- `prestiges.yml` — configured prestige order, costs, multipliers, mines, LuckPerms groups, and commands.
- `sell-prices.yml` — vanilla and ItemsAdder sell prices.
- `boosters.yml` — booster limits, stacking, item settings, and offline behavior.
- `mining.yml` — XP, AutoBlock, fortune, overflow, bulk limits, and operation caps.
- `custom-drops.yml` — restricted custom drop definitions and per-action limits.
- `block-events.yml` — block event triggers, cooldowns, milestones, and rewards.
- `leaderboards.yml` — enabled boards, metrics, periods, ordering, pagination, and season settings.
- `leaderboard-rewards.yml` — daily/weekly/monthly/seasonal competitive rewards.
- `backups.yml` — scheduled backup interval, type, and retention count.
- `guis/*.yml` — player and admin GUI layouts.

Timezone and periods:

- General period behavior uses `server.timezone` from `config.yml`.
- Statistics and leaderboard periods use documented daily, weekly ISO, monthly, and season IDs from Phase 5/6.
- Audit command date filters use UTC day boundaries.

## Integration Reference

- Vault is required for economy balance, withdrawals, deposits, selling, and reward money.
- LuckPerms is required for managed rank/prestige groups and repair.
- PlaceholderAPI registers the `relicprison` expansion when enabled and present.
- ItemsAdder is optional and used for custom blocks/items in mines, selling, drops, events, and backups diagnostics.
- AdvancedEnchantments is optional and bridged through a normalized mining pipeline.
- WorldEdit/FAWE is optional for selections/structures.
- WorldGuard is optional for mine region synchronization.
- Combat-tag integrations are optional and fail open for unsupported providers.

Diagnostics expose integration names and versions when available.

## Command Reference

Player commands:

- `/mine [mine]`
- `/rankup`
- `/rankupmax`
- `/prestige [confirm]`
- `/sellall`
- `/sellhand`
- `/sellvalue [hand]`
- `/autosell`
- `/autopickup`
- `/autosmelt`
- `/autoblock`
- `/booster status`
- `/prison`
- `/ranks`
- `/prestiges`
- `/stats`
- `/leaderboard [metric] [period]`

Administrative commands:

- `/rp status`
- `/rp menu`
- `/rp admin`
- `/rp reload [module]`
- `/rp validate`
- `/rp diagnose [detail]`
- `/rp diagnostic`
- `/rp backup list|create|info|verify|delete|restore`
- `/rp audit [page] [filters]`
- `/rp repair <player|all>`
- `/rp progression list|info|retry`
- `/rp rewards preview|finalize|history|pending|retry`
- `/rp export <player>`
- `/rp import <export-file.yml> confirm`
- `/relicmine ...`
- `/relicrank info|set|promote|demote`
- `/relicprestige info|set|promote|demote`
- `/booster give|activate|remove|setmultiplier|list`

Every important GUI action has a command alternative.

## Permission Reference

Core:

- `relicprison.use`
- `relicprison.mine.teleport`
- `relicprison.rankup`
- `relicprison.rankupmax`
- `relicprison.prestige`
- `relicprison.menu`
- `relicprison.stats`
- `relicprison.leaderboard`

Selling and boosters:

- `relicprison.sellall`
- `relicprison.sellhand`
- `relicprison.sellvalue`
- `relicprison.booster.status`
- `relicprison.booster.activate.server`

Admin:

- `relicprison.admin`
- `relicprison.admin.reload`
- `relicprison.admin.mine`
- `relicprison.admin.mine.preview`
- `relicprison.admin.mine.reset.alerts`
- `relicprison.admin.rank`
- `relicprison.admin.prestige`
- `relicprison.admin.booster`
- `relicprison.admin.sell`
- `relicprison.admin.backup`
- `relicprison.admin.diagnostic`
- `relicprison.admin.diagnostic.sensitive`
- `relicprison.admin.audit`
- `relicprison.admin.repair`
- `relicprison.admin.leaderboard`
- `relicprison.admin.transfer`

Bypasses:

- `relicprison.bypass.mine-access`
- `relicprison.bypass.teleport-warmup`
- `relicprison.bypass.combat-teleport`
- `relicprison.bypass.build`

## Placeholder Reference

Progression:

- `%relicprison_rank_progress_percent%`
- `%relicprison_rank_money_remaining%`
- `%relicprison_next_prestige_cost%`
- `%relicprison_prestige_money_remaining%`

Mines:

- `%relicprison_mine_remaining_percent%`
- `%relicprison_mine_reset_state%`
- `%relicprison_mine_reset_count%`
- `%relicprison_mine_last_reset_duration%`

Selling and boosters:

- `%relicprison_inventory_base_value%`
- `%relicprison_inventory_final_value%`
- `%relicprison_hand_base_value%`
- `%relicprison_hand_final_value%`
- `%relicprison_personal_booster_multiplier%`
- `%relicprison_personal_booster_time%`
- `%relicprison_server_booster_multiplier%`
- `%relicprison_server_booster_time%`
- `%relicprison_combined_multiplier%`

Statistics:

- `%relicprison_blocks_material_<MATERIAL>%`
- `%relicprison_blocks_mine_<mine-id>%`
- `%relicprison_normal_blocks%`
- `%relicprison_bulk_blocks%`

Leaderboards:

- `%relicprison_leaderboard_<board>_position%`
- `%relicprison_leaderboard_<board>_top_name_<position>%`
- `%relicprison_leaderboard_<board>_top_value_<position>%`
- `%relicprison_leaderboard_<board>_type%`
- `%relicprison_leaderboard_<board>_period%`
- `%relicprison_leaderboard_<board>_season_<season-id>_top_name_<position>%`
- `%relicprison_leaderboard_<board>_season_<season-id>_top_value_<position>%`

Performance:

- Placeholder evaluation is read-only.
- Placeholder value scans use short-lived bounded caches.
- Leaderboards use async refreshed cached snapshots.
- Malformed dynamic placeholders return configured fallback text.
