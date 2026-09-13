# PlaceholderAPI Placeholders

RelicPrison registers a persistent PlaceholderAPI expansion with identifier:

```text
relicprison
```

So every key below is used like:

```text
%relicprison_<key>%
```

Example:

```text
%relicprison_rank%
```

## Account and load state

```text
%relicprison_loaded%
%relicprison_balance%
%relicprison_balance_short%
%relicprison_balance_raw%
```

`loaded` is useful when you do not want a scoreboard to pretend profile data exists before the cached profile is ready.

## Rank progression

```text
%relicprison_rank%
%relicprison_rank_display%
%relicprison_next_rank%
%relicprison_next_rank_display%
%relicprison_rank_cost%
%relicprison_rank_cost_raw%
%relicprison_rank_progress_percent%
%relicprison_rank_money_remaining%
```

## Prestige progression

```text
%relicprison_prestige%
%relicprison_prestige_display%
%relicprison_next_prestige%
%relicprison_next_prestige_cost%
%relicprison_prestige_money_remaining%
```

## General statistics

```text
%relicprison_blocks%
%relicprison_blocks_lifetime%
%relicprison_blocks_daily%
%relicprison_blocks_weekly%
%relicprison_blocks_monthly%
%relicprison_money_earned%
%relicprison_items_sold%
%relicprison_rankups%
%relicprison_prestiges_count%
%relicprison_boosters_used%
%relicprison_playtime%
```

## Selling and multipliers

```text
%relicprison_multiplier%
%relicprison_sell_value%
%relicprison_inventory_base_value%
%relicprison_inventory_final_value%
%relicprison_hand_base_value%
%relicprison_hand_final_value%
%relicprison_personal_booster_multiplier%
%relicprison_personal_booster_time%
%relicprison_server_booster_multiplier%
%relicprison_server_booster_time%
%relicprison_combined_multiplier%
%relicprison_booster_count%
```

## Current mine

```text
%relicprison_mine%
%relicprison_mine_id%
%relicprison_mine_name%
%relicprison_mine_remaining%
%relicprison_mine_remaining_percent%
%relicprison_mine_mined_percent%
%relicprison_mine_reset_state%
%relicprison_mine_reset_count%
%relicprison_mine_last_reset_duration%
%relicprison_mine_next_reset%
```

## Dynamic mining statistics

Material:

```text
%relicprison_blocks_material_<MATERIAL>%
```

Example:

```text
%relicprison_blocks_material_STONE%
```

Mine ID:

```text
%relicprison_blocks_mine_<mine-id>%
```

Sources:

```text
%relicprison_normal_blocks%
%relicprison_bulk_blocks%
```

## Leaderboards

The current expansion supports dynamic board requests.

```text
%relicprison_leaderboard_<board>_position%
%relicprison_leaderboard_<board>_top_name_<position>%
%relicprison_leaderboard_<board>_top_value_<position>%
%relicprison_leaderboard_<board>_type%
%relicprison_leaderboard_<board>_period%
```

Season form:

```text
%relicprison_leaderboard_<board>_season_<season-id>_top_name_<position>%
%relicprison_leaderboard_<board>_season_<season-id>_top_value_<position>%
```

Unknown boards or malformed requests return the configured malformed/unavailable behavior rather than performing unsafe live queries.

## Gangs

Common gang values:

```text
%relicprison_gang_name%
%relicprison_gang_tag%
%relicprison_gang_rank%
%relicprison_gang_level%
%relicprison_gang_xp%
%relicprison_gang_points%
%relicprison_gang_member_count%
%relicprison_gang_online_members%
%relicprison_gang_balance%
%relicprison_gang_leaderboard_position%
```

Category leaderboard position:

```text
%relicprison_gang_leaderboard_position_<level|xp|blocks|money|prestiges|balance|block_events>%
```

Contribution/lifetime values include:

```text
%relicprison_gang_contribution_blocks%
%relicprison_gang_contribution_money%
%relicprison_gang_contribution_xp%
%relicprison_gang_contribution_rankups%
%relicprison_gang_contribution_prestiges%
%relicprison_gang_contribution_block_events%
%relicprison_gang_blocks%
%relicprison_gang_money_earned%
%relicprison_gang_prestiges%
%relicprison_gang_block_events%
```

## Performance behavior

RelicPrison's placeholder design is intentionally cache-oriented:

- profile values read cached loaded profiles;
- sell-value placeholders use a short bounded value cache;
- leaderboard placeholders use async-refreshed snapshots;
- placeholder evaluation does not intentionally run synchronous SQL;
- slow placeholder evaluations are counted for diagnostics.

Current core defaults:

```yaml
placeholders:
  value-cache-millis: 250
  leaderboard-cache-millis: 30000
  slow-threshold-millis: 10
  unavailable-text: loading
  malformed-text: invalid
```

## Example scoreboard

```text
&5&lPRISON
&7Rank: &f%relicprison_rank_display%
&7Next: &f%relicprison_next_rank_display%
&7Progress: &d%relicprison_rank_progress_percent%%
&7Mine: &f%relicprison_mine_name%
&7Mine Left: &f%relicprison_mine_remaining_percent%%
&7Multiplier: &dx%relicprison_combined_multiplier%
```
