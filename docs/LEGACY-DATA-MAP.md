# Legacy Data Map

This file records the exact old-data sources that the later migration stage must use.

| New RelicPrison field | Legacy source | Notes |
|---|---|---|
| Mine ID, world, corners | `Prison/data_storage/mines/mines/*.json` | Normalize min/max and resolve world UUID |
| Mine composition | `prisonBlocks` in each mine JSON | Parse only material and weight; ignore old counters |
| Mine reset settings | Mine JSON | Stage 7 will decide which settings remain |
| A-Z rank | Prison player JSON `ranks.default.rankName` | Database source after migration |
| Named prestige | `PrestigePlugin/prestige-data.yml` | Prefer this over old p1-p7 ladder |
| Rank costs | `PrestigePlugin/config.yml` `base-rankup-costs` | A is the starting rank, A entry is cost to B |
| Prestige costs/multipliers | `PrestigePlugin/config.yml` | Coal through Immortal |
| Sell prices | `SellAllCustom/config.yml` | Active price source |
| LuckPerms rank/prestige structure | LuckPerms export | Use for later group synchronization, not player progression truth |
| Player lifetime blocks | Prison player JSON `totalBlocks` | Validate nonnegative values |
| Player UUID/name | Prison player JSON | Bedrock-style identifiers require special validation |

## Mine names found

A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z, coal, diamond, emerald, gold, immortal, iron, pvp, redstone

## Rank names found

A, B, C, D, E, F, G, H, I, J, K, Kos, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z, p1, p2, p3, p4, p5, p6, p7
