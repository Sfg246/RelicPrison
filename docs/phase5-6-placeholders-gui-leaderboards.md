# RelicPrison Phase 5-6: Placeholders, GUI, Leaderboards

## PlaceholderAPI

All placeholders are read-only. Inventory value placeholders use the same sell services as `/sellvalue` and are cached by `placeholders.value-cache-millis`. Leaderboard placeholders use cached async snapshots and return `placeholders.unavailable-text` while a snapshot is loading.

### Progression

- `%relicprison_rank_progress_percent%`
- `%relicprison_rank_money_remaining%`
- `%relicprison_next_prestige_cost%`
- `%relicprison_prestige_money_remaining%`

### Mines

- `%relicprison_mine_remaining_percent%`
- `%relicprison_mine_reset_state%`
- `%relicprison_mine_reset_count%`
- `%relicprison_mine_last_reset_duration%`

### Selling and Boosters

- `%relicprison_inventory_base_value%`
- `%relicprison_inventory_final_value%`
- `%relicprison_hand_base_value%`
- `%relicprison_hand_final_value%`
- `%relicprison_personal_booster_multiplier%`
- `%relicprison_personal_booster_time%`
- `%relicprison_server_booster_multiplier%`
- `%relicprison_server_booster_time%`
- `%relicprison_combined_multiplier%`

Time placeholders use the documented compact format: `1d 2h`, `3h 4m`, `5m 6s`, or `7s`.

### Statistics

- `%relicprison_blocks_material_DIAMOND_ORE%`
- `%relicprison_blocks_mine_a%`
- `%relicprison_normal_blocks%`
- `%relicprison_bulk_blocks%`

Material IDs are validated with Bukkit material matching. Mine IDs are normalized with the mine service and return `0` if unknown or malformed.

### Leaderboards

Configured board IDs come from `leaderboards.yml` and are normalized to lowercase underscores.

- `%relicprison_leaderboard_<board>_position%`
- `%relicprison_leaderboard_<board>_top_name_<position>%`
- `%relicprison_leaderboard_<board>_top_value_<position>%`
- `%relicprison_leaderboard_<board>_type%`
- `%relicprison_leaderboard_<board>_period%`
- `%relicprison_leaderboard_<board>_season_<season-id>_top_name_<position>%`
- `%relicprison_leaderboard_<board>_season_<season-id>_top_value_<position>%`
- `%relicprison_leaderboard_<board>_season_<season-id>_position%`

Malformed dynamic placeholders return `placeholders.malformed-text`.

## GUI

GUI titles and documented menu behavior live in:

- `guis/mines.yml`
- `guis/progression.yml`
- `guis/prestige.yml`
- `guis/selling.yml`
- `guis/boosters.yml`
- `guis/statistics.yml`
- `guis/admin.yml`

Player menus:

- Main prison menu: `/prison` or `/rp menu`
- Mine selector: `/mine [mine]`
- Rank progression: `/ranks`, `/rankup`, `/rankupmax`
- Rankup confirmation and Rankup Max confirmation: GUI confirmation or command revalidation
- Prestige and prestige confirmation: `/prestiges`, `/prestige`
- Selling information: `/sellvalue [hand]`, `/sellall`, `/sellhand`
- Booster status: `/booster status`
- Statistics: `/stats`
- Leaderboards: `/leaderboard [metric] [period]`

Admin menus:

- Mine manager: `/rp admin`, `/relicmine list|gui|create|delete|confirm`
- Composition editor: `/relicmine composition`
- Reset settings editor: `/relicmine resetconfig`, `/relicmine reset`
- Rank editor: `/relicrank info|set|promote|demote`
- Prestige editor: `/relicprestige info|set|promote|demote`
- Sell-price editor: `/rp reload selling`
- Booster manager: `/booster give|activate|remove|setmultiplier|list`
- Block Event editor: `/rp reload block-events`
- Integration status: `/rp status`
- Diagnostics: `/rp validate`, `/rp diagnostic`
- Player progression manager: `/rp progression list|info|retry`

GUI clicks use PDC action and session identifiers. Shift-clicks, number-key swaps, double-click collection, hotbar/offhand swaps, drags, drops, and creative-mode edits are cancelled. Rankup, Rankup Max, and Prestige confirmations revalidate profile state, permissions, balance, price, and target at click time.

## Leaderboards

Boards are configured in `leaderboards.yml`. Supported metrics:

- `blocks`
- `rank`
- `prestige`
- `money`
- `items_sold`
- `rankups`
- `prestiges`
- `boosters`
- `playtime`

Supported periods:

- `lifetime`
- `daily`
- `weekly`
- `monthly`
- `season`

Rank and prestige boards use configured rank/prestige order. Ties sort deterministically by value descending, then name ascending, then UUID ascending. Reads use async cached snapshots refreshed by `leaderboards.refresh-seconds`; placeholders never wait for SQL.

## Period Behavior

- Timezone: `server.timezone`.
- Daily ID: local date, e.g. `2026-07-31`.
- Weekly ID: ISO week with Monday start, e.g. `2026-W31`.
- Monthly ID: local year-month, e.g. `2026-07`.
- Season ID: `leaderboards.season.id`.
- DST: period IDs are based on local calendar dates, not fixed hour counts.
- Downtime: reward finalization defaults to the previous completed period at command time.

## Reward Ledger

Migration 7 adds:

- `rp_leaderboard_reward_periods`
- `rp_leaderboard_reward_ledger`

Finalization snapshots winners before delivery and reserves the `(board_id, period_id)` once. Each reward inserts a ledger row keyed by `(board_id, period_id, player_uuid, reward_definition_id)` before non-idempotent delivery. States are `RESERVED`, `DELIVERED`, `PENDING_OFFLINE`, `RETRY`, and `STAFF_REVIEW`.

Reward commands:

- `/rp rewards preview <board> [period-id]`
- `/rp rewards finalize <board> [period-id] confirm`
- `/rp rewards history [limit]`
- `/rp rewards pending`
- `/rp rewards retry <board> <period-id> <uuid> <reward-id>`

Permission: `relicprison.admin.leaderboard`.

Command rewards execute only on the main thread and are capped by `leaderboards.rewards.command-limit-per-period`. Offline item and Vault money rewards are held as `PENDING_OFFLINE` and retried on join. External command compensation is documented as staff-review work if an external command succeeds but the server crashes before the ledger reaches `DELIVERED`.

## Staging Checklist

Do not mark these manual checks passed unless performed on a Paper server with the relevant client/plugin installed.

- Java player workflow: `/prison`, `/mine`, `/ranks`, `/prestiges`, `/stats`, `/leaderboard`.
- Bedrock player workflow through Geyser: touch navigation, pagination density, confirmation clicks.
- Rankup and Rankup Max confirmation: stale price, insufficient balance, removed permission.
- Prestige confirmation: stale prestige, removed permission, not max rank.
- Selling display: inventory and hand value match `/sellvalue`.
- Booster status: personal/server multiplier and time match `/booster status`.
- Statistics: normal and bulk counters match mining pipeline diagnostics.
- Leaderboards: cached menu and PlaceholderAPI output match database snapshots.
- Mine editing: command alternatives and existing `/relicmine gui`.
- Composition editing: command alternatives and reload validation.
- Block Event editing: `/rp reload block-events` validation and disabled overhead.
- Reward finalization: preview, finalize once, history, pending, retry.
- Offline reward delivery: queue item/money reward, join once, verify no duplicate.
- Restart after reward finalization: finalized period cannot deliver twice.

