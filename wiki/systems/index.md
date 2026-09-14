# RelicPrison Systems

This section explains the major RelicPrison systems as complete workflows instead of isolated settings. Start with the system you are trying to build, then use the generated YAML reference when you need the exact key name.

## Core gameplay

| System | What it controls | Start here |
| --- | --- | --- |
| Mines & resets | Mine regions, composition, access, resets, spawns, copy/move | [Mines & Resets](/systems/mines) |
| Mining pipeline | Normal and bulk mining, fortune, AutoPickup, AutoSell, AutoBlock, custom drops | [Mining Pipeline](/systems/mining) |
| Ranks & prestiges | Rank requirements, prestige loops, permission groups, mine access | [Ranks & Prestiges](/systems/progression) |
| Economy | Selling, prices, multipliers and Vault-backed money flow | [Economy](/systems/economy) |
| Boosters | Timed multipliers, stacking, booster items and permissions | [Boosters](/systems/boosters) |
| Block Events | Chance/target-based mining events and durable rewards | [Block Events](/systems/block-events) |
| Gangs | Membership, ranks, banks, missions, upgrades, chat, seasons | [Gangs](/systems/gangs) |

## Competition, rewards and presentation

| System | What it controls | Start here |
| --- | --- | --- |
| Leaderboards | Ranked boards, periods, finalization and winner rewards | [Leaderboards](/systems/leaderboards) |
| Reward ledger | Crash-aware reward packages and component delivery | [Rewards & Recovery](/systems/rewards) |
| Statistics | Player/mine statistics, dimensions and leaderboard inputs | [Statistics](/systems/statistics) |
| GUIs | Player menus, admin editors, themes and confirmation screens | [GUIs & Admin Editors](/systems/guis) |

## A simple mental model

A block is mined, RelicPrison decides whether the block belongs to a managed mine, calculates the mining result, routes drops or selling, commits statistics/rewards, updates progression-facing data, and finally shows the result through messages, GUIs and placeholders. Admin systems wrap that gameplay with validation, backups, diagnostics and recovery.

::: tip New server owner?
Do not configure every system at once. Follow the [Zero-to-Launch Course](/course/) and enable one layer at a time.
:::

## Exact configuration values

System pages explain behavior. The [generated configuration reference](/generated/config/) is the source-driven list of every packaged YAML setting and default value.
