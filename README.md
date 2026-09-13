# RelicPrison

RelicPrison is the Paper-only prison core for RelicWorld. This source package is a release candidate through Stage 23 and must still pass real Paper/integration staging before being treated as production-ready.

## Requirements

- Paper 1.21.x and Java 25
- Vault plus a Vault-compatible economy provider
- LuckPerms Bukkit 5.4.164
- AdvancedEnchantments 9.22.9 when its integration is enabled
- ItemsAdder 4.0.16 when custom items or custom blocks are enabled
- PlaceholderAPI 2.11.6 for `%relicprison_*%` placeholders
- WorldEdit or FAWE and WorldGuard are optional integrations

## Implemented systems

- Mines, weighted compositions, adaptive resets, evacuation, access control, and teleports
- A-Z ranks and Coal-Immortal prestiges with Vault and LuckPerms synchronization
- Selling, multipliers, persistent boosters, AutoPickup, AutoSell, AutoSmelt, AutoBlock, Fortune, XP, and durability
- AdvancedEnchantments bridge that attempts to submit exposed AE bulk block collections through the shared mining pipeline
- ItemsAdder custom blocks, custom items, custom drops, mine compositions, and custom sell prices
- PlaceholderAPI expansion and player chest menus with command alternatives
- Player, mine, selling, progression, booster, and playtime statistics
- Daily, weekly, monthly, and lifetime block leaderboards plus lifetime economy and progression leaderboards
- Automatic backups, validation, diagnostics, diagnostic exports, staff auditing, progression repair, and player export/import
- Native prison gangs with persistent ranks, invitations, bank, upgrades, progression, missions, boosters, leaderboards, seasons, GUIs, audit history, and multi-server transaction protection
- A unified premium inventory design system with semantic materials, state-aware glow, structured lore, consistent framing/navigation, pagination, and protected confirmations across every player, gang, and administration GUI
- A centralized command presentation system with configurable RelicPrison/Gang prefixes, consistent semantic colors, descriptive outcomes, structured status panels, useful usage errors, and paginated player/staff help menus

## Release status

- Recommended version label: `RelicPrison 1.0.0-rc6-stage6`.
- Do not label this build `1.0.0` until real Paper 1.21.10 staging passes with Vault, a supported economy provider, LuckPerms, PlaceholderAPI, ItemsAdder, AdvancedEnchantments, Java client, Bedrock/Geyser/Floodgate, backup restore, and rollback.
- Current known limitations are tracked in `docs/CURRENT_STATE.md`.
- The packaged 33-mine configuration is intentional and is preserved unchanged.

## Build

```bash
./mvnw clean verify
```

Expected Maven artifacts after this RC pass:

```text
target/RelicPrison-1.0.0-rc6-stage6.jar
target/RelicPrison-1.0.0-rc6-stage6-sources.jar
```

## Upgrade from Stage 5

1. Fully stop the server.
2. Remove every older RelicPrison JAR from `plugins/`.
3. Install the generated RC JAR.
4. Keep the existing `plugins/RelicPrison/` folder, database, mines, ranks, prestiges, and configuration files.
5. Start normally. Do not replace the JAR through Bukkit `/reload` or DeepReload.
6. Run `/rp validate` and test the new systems on staging.

Database schemas are upgraded automatically through version 17. Existing configuration files are not overwritten. New missing files, including `gangs.yml` and `guis/gangs.yml`, are generated on first startup.

## Inventory design

Stage 22 redesigns every inventory created by RelicPrison through the shared `GuiItemBuilder`, `GuiVisuals`, `GuiLayout`, and `GuiThemeConfig` layer. Materials, state colors, glow, filler panes, navigation, confirmations, menu titles, and sounds have polished defaults in `guis/theme.yml` and the section-specific files under `guis/`. See `docs/GUI-DESIGN.md` for the complete audit and representative layouts.

## Native gangs

`/gang` opens the gang GUI. `/gc` sends or toggles gang chat, and `/relicgang` provides audited staff administration. Gang membership and ranks remain internal; LuckPerms only gates global access and configurable member-limit permissions. See `docs/GANGS.md` for commands, permissions, placeholders, configuration, and the schema table summary.

## Command presentation

Stage 23 routes every command handler through the shared `MessageService` presentation layer. Use `/prison help`, `/gang help`, `/booster help`, `/relicmine help`, `/relicgang help`, `/relicrank`, `/relicprestige`, or `/relicprison help` for categorized command menus. Multi-page menus accept a page number after `help`, such as `/gang help 2`.

## Stage 15-18 feature flags

Enable only the systems you intend to use:

```yaml
features:
  itemsadder: true
  custom-block-drops: false
  player-leaderboards: true
  player-mining-statistics: true
  mine-analytics: true
```

`features.itemsadder` must be enabled before ItemsAdder custom mine blocks, custom drops, or custom sell prices are active. `features.custom-block-drops` enables the replacement rules in `custom-drops.yml`. `features.player-mining-statistics` controls approved per-player mining statistics and defaults on. `features.mine-analytics` controls only mine-level analytics and can remain disabled without disabling player statistics. `features.player-leaderboards` allows leaderboard menus.

RC6 Stage 5 adds the native prison-focused Gangs system without land claiming, chunk power, raiding, or survival-Factions mechanics. The database is authoritative for membership, spending, upgrades, progression, mission claims, seasons, and historical records. The packaged 33-mine configuration remains unchanged.

Backend recovery follow-up: bulk statistics, sold-item totals, money-earned progression, and profile block
progression commit through the unique bulk SQL ledger. Bulk XP and Vault deposits now freeze absolute targets,
so recovery applies only an unpaid remainder. AutoPickup, AutoSell, AutoBlock, and fallback-drop dimensions are
created only from the frozen actual delivery result. A block with mixed sold and picked output uses AutoSell as
its one primary route; AutoBlock is an independent transformation dimension. Generic command execution and
Paper player/entity persistence still have an external commit gap, so this source remains Stage 3.

## Main player commands

```text
/mine [mine]
/rankup
/rankupmax
/prestige [confirm]
/sellall
/sellhand
/sellvalue [hand]
/booster status
/prison
/ranks
/prestiges
/stats
/leaderboard
/gang
/gc [message]
```

AutoPickup, AutoSell, AutoSmelt, and AutoBlock are controlled globally by `config.yml`. Their commands display server status rather than storing per-player overrides.

## Stage 18 administration

```text
/rp status
/rp reload <module|all>
/rp validate
/rp diagnose [detail]
/rp diagnostic
/rp backup list
/rp backup create <full|config|data>
/rp backup info <id>
/rp backup verify <id>
/rp backup delete <id> confirm
/rp backup restore <id> confirm
/rp audit [filters...]
/rp repair <player|all>
/rp export <player>
/rp import <export-file.yml> confirm
```

SQLite full/config/data restore requests are applied during the next full startup and create a pre-restore safety archive. MySQL full/data backups contain `database-export.sql`; importing MySQL data is manual. Automatic MySQL restore is intentionally limited to configuration-only backups.

## PlaceholderAPI

Expansion identifier: `relicprison`

Common placeholders:

```text
%relicprison_rank%
%relicprison_rank_display%
%relicprison_next_rank%
%relicprison_rank_cost%
%relicprison_prestige%
%relicprison_blocks_lifetime%
%relicprison_blocks_daily%
%relicprison_money_earned%
%relicprison_multiplier%
%relicprison_mine_name%
%relicprison_mine_mined_percent%
%relicprison_mine_next_reset%
%relicprison_items_sold%
%relicprison_playtime%
```

## ItemsAdder configuration

Custom sell prices use namespaced IDs in `sell-prices.yml`:

```yaml
custom-prices:
  mysteryblock:mystery_block: 500.0
```

Custom drop rules use vanilla materials or ItemsAdder namespaced IDs in `custom-drops.yml`. Mine compositions may also contain ItemsAdder block IDs. Run `/rp validate` after editing these files.

## Documentation

- `docs/INSTALL-UPGRADE-CONFIG-REFERENCE.md`
- `docs/MASTER_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT_STATE.md`
- `docs/TESTING.md`
- `docs/DIAGNOSTICS-AUDITING-BACKUP-RESTORE.md`
- `docs/STAGING-PRODUCTION-ROLLBACK-MONITORING.md`
- `docs/API-THREAD-SAFETY.md`
- `docs/STAGES-15-18-COMPLETION.md`
- `docs/STAGES-11-14-COMPLETION.md`
- `docs/STAGES-7-10-COMPLETION.md`
- `docs/STAGES-1-6-COMPLETION.md`
- `docs/BUILD-VERIFICATION.md`

## Formatted balance placeholders

```text
%relicprison_balance%        # $1,250,000.00
%relicprison_balance_short%  # $1.25M
%relicprison_balance_raw%    # 1250000.00
```

These use the `formatting` section of `config.yml` and the active Vault economy provider.

