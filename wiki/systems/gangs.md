# Gangs

RelicPrison gangs are social/progression groups built for prison gameplay.

::: info Not Factions
Gangs do not implement chunk claiming, TNT raiding, power, or territory warfare. Their job is prison progression: members, roles, bank, upgrades, missions, chat, contributions, leaderboards, and seasons.
:::

## Create a gang

```text
/gang create <name> <tag>
```

Example:

```text
/gang create Miners MNR
```

The exact validation limits and creation cost come from `gangs.yml`.

## Invite and join

Invite:

```text
/gang invite Steve
```

The invited player can inspect pending invitations:

```text
/gang invites
```

Then accept or deny by invitation ID:

```text
/gang accept <invite-id>
/gang deny <invite-id>
```

An open gang can also be joined by name or tag when its privacy/configuration allows it:

```text
/gang join <name|tag>
```

## Member management

```text
/gang leave
/gang kick <player>
/gang promote <player>
/gang demote <player>
/gang transfer <player>
```

Ownership has extra protections. The Owner rank is not a normal disposable custom rank, and ownership must be transferred or the gang disbanded before the owner can simply leave.

## Gang bank

```text
/gang bank
/gang bank balance
/gang bank history
/gang bank deposit <amount> [reason]
/gang bank withdraw <amount> [reason]
```

Bank operations are permissioned through gang-internal rank permissions and protected with persistent transaction/history data.

## Upgrades and missions

```text
/gang upgrades
/gang upgrades <upgrade>
/gang missions
/gang missions claim <mission-id>
```

Costs, objectives, rewards, progression curves, and limits live in `gangs.yml`, not as mystery values you have to hunt for in Java.

## Gang ranks

Default rank names are:

```text
Owner
Co-Leader
Officer
Veteran
Member
Recruit
```

Custom ranks can carry internal gang permissions such as:

```text
invite
kick
promote
demote
manage_ranks
manage_rank_permissions
deposit_bank
withdraw_bank
purchase_upgrades
manage_gang_boosters
edit_identity
edit_privacy
set_gang_home
use_gang_home
manage_gang_chat
view_audit_log
transfer_ownership
disband
```

These are **internal gang permissions**. RelicPrison does not create a LuckPerms group for every gang rank.

## Gang chat

```text
/gang chat toggle
/gang chat <message>
/gc <message>
```

Global Bukkit permission:

```text
relicprison.gang.chat
```

## Identity and privacy

```text
/gang settings <field> <value>
```

Supported settings include gang name, tag, description, color, MOTD, and privacy paths in the current implementation.

## Gang home

```text
/gang sethome
/gang home
```

The member's internal gang rank must allow the relevant home permission.

## Disbanding

Player-owner path:

```text
/gang disband confirm
```

Administrative path:

```text
/relicgang disband <gang> confirm
```

<div class="danger-zone"><strong>Disband is destructive.</strong><br>Use the confirmation path and take operational backups before large administrative cleanups.</div>

## Server-admin commands

Discovery:

```text
/relicgang list [query]
/relicgang inspect <name|tag>
/relicgang audit <gang>
```

Members and identity:

```text
/relicgang addmember <gang> <player>
/relicgang removemember <gang> <player>
/relicgang setowner <gang> <player>
/relicgang rename <gang> <name>
/relicgang tag <gang> <tag>
```

Progression/operations:

```text
/relicgang setlevel <gang> <amount>
/relicgang addlevel <gang> <amount>
/relicgang setxp <gang> <amount>
/relicgang addxp <gang> <amount>
/relicgang setpoints <gang> <amount>
/relicgang addpoints <gang> <amount>
/relicgang adjustbank <gang> <signed-amount> [reason]
/relicgang reload
```

Seasons:

```text
/relicgang season create <id> <starts-ms> <ends-ms> <categories-csv> [reward-plan]
/relicgang season finalize <id>
/relicgang season results <id>
```

## Global gang permissions

```text
relicprison.gang.use
relicprison.gang.create
relicprison.gang.join
relicprison.gang.chat
relicprison.gang.admin
relicprison.gang.limit.20
relicprison.gang.limit.30
```

## Gang PlaceholderAPI values

Prefix these with `%relicprison_...%`:

```text
gang_name
gang_tag
gang_rank
gang_level
gang_xp
gang_points
gang_member_count
gang_online_members
gang_balance
gang_leaderboard_position
gang_leaderboard_position_<category>
gang_contribution_blocks
gang_contribution_money
gang_contribution_xp
gang_contribution_rankups
gang_contribution_prestiges
gang_contribution_block_events
gang_blocks
gang_money_earned
gang_prestiges
gang_block_events
```

## Multi-server note

Gang state uses database constraints/transactions for sensitive operations such as membership, invitations, ownership, bank spending, upgrades, mission claims, and season finalization. Online caches refresh after reconnect/cross-server changes.

That does not remove the need to stage-test two real servers when you deploy a shared-database prison network.
