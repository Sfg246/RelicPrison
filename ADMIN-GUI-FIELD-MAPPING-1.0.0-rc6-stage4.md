# Admin GUI Field-to-Runtime Mapping

## Rank Editor

| GUI field | Persisted path | Runtime consumer |
|---|---|---|
| ID | `ranks.<id>` | `RankRepository`, profile progression references |
| Display name | `display-name` | rank menus and progression messages |
| Order | `order` | `RankServiceImpl` ordering and progression traversal |
| Cost | `next-cost` | `ProgressionServiceImpl` cumulative Vault charge |
| Requirements | `requirements` | progression service and confirmation GUI requirement checks |
| Permission | `permission` | progression service and confirmation GUI permission checks |
| Mine association | `mine` | `MineAccessServiceImpl.currentRankMine` |
| Display material/lore | `display-material`, `lore` | rank GUI rendering |
| Enabled | `enabled` | rank traversal; disabled ranks are skipped |
| Multiplier/effects | `sell-multiplier`, LuckPerms group, enter/leave commands | multiplier and progression transaction effect paths |

## Prestige Editor

| GUI field | Persisted path | Runtime consumer |
|---|---|---|
| ID/display/order/cost | `prestiges.<id>`, `display-name`, `order`, `cost` | prestige catalog, GUI, progression traversal and Vault charge |
| Multipliers | `sell-multiplier`, `rank-cost-multiplier` | sell multiplier and rank cost calculation |
| Requirements/permission | `requirements`, `permission` | progression service and confirmation GUI checks |
| Effects | LuckPerms group, enter/leave commands | progression transaction external effects |
| Mine association | `mine` | current-mine fallback when the active rank has no mine |
| Display material/lore | `display-material`, `lore` | prestige GUI rendering |
| Enabled | `enabled` | prestige traversal; disabled entries are skipped |

## Sell-Price Editor

| GUI field/action | Persisted path | Runtime consumer |
|---|---|---|
| Vanilla price | `prices.<MATERIAL>` | `SellPriceCatalog` material valuation |
| Custom price | `custom-prices.<namespace>.<id>` | ItemsAdder custom item/block valuation |
| Add/edit/remove | same price nodes | atomic catalog reload after validation |
| Search/pagination | editor snapshot | GUI list filtering and 36-entry pages |

Custom IDs require a connected/enabled provider and a positive registry lookup. Prices reject negative, NaN, and infinity.

## Booster Manager

| GUI field/action | Persistence/runtime consumer |
|---|---|
| ID, multiplier, duration | booster database row and `BoosterServiceImpl` multiplier caches |
| Scope and target | server/personal table plus player UUID; scope changes use one SQL transaction |
| Start now/schedule | `starts_at`; expiry task activates due scheduled boosters |
| Enabled/disabled | `enabled`; disabled boosters remain persisted but do not multiply |
| Active/scheduled/remaining | derived from `starts_at`, `expires_at`, `enabled` for GUI state |
| Cancel | deletes the managed row and invalidates multiplier caches |
| Restart/reconnect | repository reload rebuilds active, scheduled, and disabled caches |

## Block Event Editor

All editable nodes are consumed by `BlockEventCatalog`/`BlockEventService`: ID, display name, priority, trigger, mining type, chance, cooldown, every-X count, daily target, vanilla/custom scope, mine scope, minimum rank/prestige, permissions, per-action limits, money, item, XP, command, announcement, booster rewards, and enabled state. Enabled entries require a valid trigger and at least one valid reward.

## Reset-Settings Editor

All editable nodes load into `MineResetConfig` and are consumed by `MineResetServiceImpl`: timed schedule, interval, percentage threshold, countdown, warning points, notification scope/radius, evacuation, reset order, lifecycle commands, retry count/delay, recount behavior, teleport destination, outside-destination safety, and enabled state. GUI changes require explicit confirmation.

## Mine Composition Editor

| GUI field | Runtime consumer |
|---|---|
| Vanilla/custom provider ID | compiled reset composition and ItemsAdder placement |
| Weight/normalized percentage | `CompiledComposition` weighted sampler |
| Minimum prestige | reset-time eligible composition filtering |
| `fallback-material` metadata | actual vanilla fallback when ItemsAdder placement fails |
| Explicit air | permits and places `Material.AIR` only when explicitly enabled |

The detail view reports total weight and every configured entry. Unsupported provider metadata is rejected rather than ignored.
