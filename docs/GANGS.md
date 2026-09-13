# Native Gangs

RelicPrison gangs are prison progression groups. They do not implement land claiming, chunk power, TNT raiding, territory warfare, or survival-Factions mechanics. Gang IDs and player UUIDs are authoritative; editable names and tags are never identity keys.

## Player Commands

- `/gang` - open the main gang GUI.
- `/gang create <name> <tag>` - create a gang.
- `/gang invite <player>`; `/gang invites`; `/gang accept <invite-id>`; `/gang deny <invite-id>`.
- `/gang join <name|tag>`; `/gang leave`; `/gang kick <player>`.
- `/gang promote <player>`; `/gang demote <player>`; `/gang transfer <player>`.
- `/gang disband confirm` - confirm disband within 30 seconds.
- `/gang bank [balance|history|deposit <amount> [reason]|withdraw <amount> [reason]]`.
- `/gang upgrade [upgrade-id]`; `/gang missions`; `/gang missions claim <mission-id>`.
- `/gang ranks`; `/gang rank create|edit|permission|delete ...`.
- `/gang chat [toggle|message]`; `/gc [message]`.
- `/gang settings <name|tag|description|color|motd|privacy> <value>`.
- `/gang sethome`; `/gang home`.

## Admin Commands

- `/relicgang list`; `/relicgang search [query]`; `/relicgang inspect <gang>`.
- `/relicgang disband <gang> confirm`.
- `/relicgang addmember <gang> <player>`; `/relicgang removemember <gang> <player>`; `/relicgang setowner <gang> <player>`.
- `/relicgang setlevel|addlevel|setxp|addxp|setpoints|addpoints <gang> <amount>`.
- `/relicgang adjustbank <gang> <signed-amount> [reason]`.
- `/relicgang rename <gang> <name>`; `/relicgang tag <gang> <tag>`.
- `/relicgang audit <gang>`; `/relicgang reload`.
- `/relicgang season create <id> <starts-ms> <ends-ms> <categories-csv> [reward-plan]`.
- `/relicgang season finalize <id>`; `/relicgang season results <id>`.

Every admin mutation records through the existing staff audit service. Gang-local mutations also append immutable gang audit records.

## Global Permissions

- `relicprison.gang.use` - use `/gang` and its GUI.
- `relicprison.gang.create` - create a gang.
- `relicprison.gang.join` - join open gangs and accept invitations.
- `relicprison.gang.chat` - use `/gc`.
- `relicprison.gang.admin` - use `/relicgang`; inherited by `relicprison.admin`.
- `relicprison.gang.limit.20`, `relicprison.gang.limit.30` - configurable creation-time member limits.

No LuckPerms group is created per gang. Membership and rank authorization remain internal.

## Internal Rank Permissions

`invite`, `kick`, `promote`, `demote`, `manage_ranks`, `manage_rank_permissions`, `deposit_bank`, `withdraw_bank`, `purchase_upgrades`, `manage_gang_boosters`, `edit_identity`, `edit_privacy`, `set_gang_home`, `use_gang_home`, `manage_gang_chat`, `view_audit_log`, `transfer_ownership`, and `disband`.

Default ranks are Owner, Co-Leader, Officer, Veteran, Member, and Recruit. Each row has a stable UUID, display name, priority, color, and permission set. Owner is undeletable, keeps every internal permission, and cannot leave until ownership is transferred or the gang is disbanded. Deleting a custom rank requires an existing fallback rank and moves affected members atomically.

## PlaceholderAPI

Prefix every key with `%relicprison_...%`:

- `gang_name`, `gang_tag`, `gang_rank`, `gang_level`, `gang_xp`, `gang_points`.
- `gang_member_count`, `gang_online_members`, `gang_balance`.
- `gang_leaderboard_position` and `gang_leaderboard_position_<level|xp|blocks|money|prestiges|balance|block_events>`.
- `gang_contribution_blocks`, `gang_contribution_money`, `gang_contribution_xp`, `gang_contribution_rankups`, `gang_contribution_prestiges`, `gang_contribution_block_events`.
- `gang_blocks`, `gang_money_earned`, `gang_prestiges`, `gang_block_events`.

Placeholder reads use refreshed caches and never perform SQL on the Paper server thread.

## Configuration

- `gangs.yml` contains validation, creation cost, member limits, invitation timeout, bank limits, progression curve and XP sources, default ranks, upgrades, boosters, missions, leaderboard categories, seasons, messages, and GUI behavior.
- `guis/gangs.yml` contains titles for the gang GUI and all gang submenus.
- Economy-sensitive XP, costs, limits, multipliers, mission targets, and rewards are configuration values rather than Java constants.
- Season reward plans use `positions=component|component;positions=component`, for example `1=money:1000000;2,3=money:500000`. Supported shared-ledger components are `money`, `experience`, `command`, and `announcement`.

## Database Tables

- `rp_gangs` - stable identity, editable metadata, owner, creation time, progression, points, bank, limits, privacy, color, MOTD, optional home, enabled/version state.
- `rp_gang_ranks`, `rp_gang_rank_permissions` - stable rank identities, hierarchy, colors, system flags, and permissions.
- `rp_gang_members`, `rp_gang_invites` - one-gang-per-player membership and persistent expiring invitations.
- `rp_gang_bank_transactions`, `rp_gang_withdrawal_usage` - immutable balances-before/after ledger and daily withdrawal counters.
- `rp_gang_upgrades` - purchased tiers and optimistic versions.
- `rp_gang_statistics`, `rp_gang_member_statistics`, `rp_gang_period_statistics` - lifetime/member and daily/weekly/monthly/season committed totals.
- `rp_gang_boosters` - scheduled/active/expired gang booster records recovered on restart/reconnect.
- `rp_gang_missions`, `rp_gang_mission_progress` - period instances, objective progress, completion, and claims.
- `rp_gang_audit` - retained gang-local history.
- `rp_gang_seasons`, `rp_gang_season_results` - season definitions and immutable frozen winners/reward state.
- `rp_gang_operation_claims` - deterministic exactly-once claims for contributions, mission rewards, and other replayable operations.

Migration 17 is ordered and idempotent. Startup stops safely on migration failure or a database version newer than the plugin supports.

## Multi-Server Behavior

Unique constraints and transactions protect joining, invitations, membership changes, bank spending, ownership transfer, rank changes, upgrade purchases, progression, point spending, mission claims, reward creation, and season finalization. Periodic cache refresh and the database reconnect listener reload online membership, multipliers, boosters, statistics, and leaderboard positions.

## Migration Boundary

`GangMigrationProvider` and `GangMigrationService` provide preview and explicit confirmation boundaries for importing a known external gang dataset. No Factions schema is guessed and no foreign plugin classes are loaded by the core. A concrete provider must be supplied for the exact approved source implementation.
