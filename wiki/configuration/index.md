# Configuration Map

RelicPrison splits configuration by responsibility so one giant file does not own the entire plugin.

## The rule before editing

Always know which file owns the behavior you are changing.

| File | Owns |
|---|---|
| `config.yml` | Core feature switches, timezone, mine-reset engine, access, teleport, progression defaults, logging, number formatting, placeholder caches, GUI security, leaderboard runtime settings |
| `storage.yml` | SQLite/MySQL, queue/retry behavior, player-load timeout, save interval, shutdown flush |
| `integrations.yml` | Vault, LuckPerms, WorldGuard, WorldEdit/FAWE, ItemsAdder, AdvancedEnchantments, PlaceholderAPI, Geyser/Floodgate awareness, combat provider |
| `mines.yml` | Mine definitions, bounds, spawn, composition, requirements, reset rules, metadata/hooks |
| `ranks.yml` | Rank order, costs, mine mapping, progression definitions |
| `prestiges.yml` | Prestige tiers, costs, sell multipliers, rank-cost multipliers, mine mapping |
| `sell-prices.yml` | Sellable vanilla/custom items and their prices |
| `boosters.yml` | Booster limits, stacking/item behavior, durations/offline behavior |
| `mining.yml` | Mining pipeline limits, XP, bulk behavior, fortune/conversion-related settings |
| `custom-drops.yml` | Custom drop definitions and limits |
| `block-events.yml` | Mining-triggered events, cooldowns, milestones, reward plans |
| `gangs.yml` | Gang validation, member limits, bank, ranks, upgrades, missions, boosters, leaderboards, seasons |
| `leaderboards.yml` | Player leaderboard boards, metrics, periods, sorting, pages, seasons |
| `leaderboard-rewards.yml` | Competitive reward plans by period/position |
| `backups.yml` | Scheduled backup type, interval, retention |
| `messages.yml` | Player/staff messages and help presentation |
| `guis/*.yml` | Inventory titles, items, lore, slots, navigation, presentation |

## Which file do I edit?

<div class="journey-grid">
<div class="reference-card"><strong>“I want AutoSell on.”</strong><br><br>Edit <code>config.yml</code> feature switches, then verify related selling configuration.</div>
<div class="reference-card"><strong>“Rank B costs too much.”</strong><br><br>Edit <code>ranks.yml</code>.</div>
<div class="reference-card"><strong>“Mine A has the wrong blocks.”</strong><br><br>Prefer mine admin commands for composition or edit <code>mines.yml</code> carefully.</div>
<div class="reference-card"><strong>“I want ItemsAdder blocks.”</strong><br><br>Enable the ItemsAdder bridge in <code>integrations.yml</code>, then configure supported content in the owning feature file.</div>
<div class="reference-card"><strong>“Gang member limit is wrong.”</strong><br><br>Edit <code>gangs.yml</code>.</div>
<div class="reference-card"><strong>“The GUI button is ugly.”</strong><br><br>Edit the relevant file under <code>guis/</code>, not database/player state.</div>
</div>

## Reload vs restart

RelicPrison has a controlled reload path:

```text
/rp reload [module]
```

Use plugin-supported reload behavior for configuration changes that are documented as reloadable.

For changes involving Java/plugin versions, storage/database topology, dependency installation, or a pending restore, perform a normal server restart.

::: warning Do not use random plugin hot-reloaders
Hot-reloading a complex plugin can leave listeners, services, tasks, database executors, or third-party integrations in states the plugin never promised to support. Prefer RelicPrison's own reload command or a clean restart.
:::

## Validate after changes

Run:

```text
/rp validate
/rp diagnose
```

For staging changes, also test the exact feature you edited. A YAML file being syntactically valid does not prove the gameplay result is what you intended.

## Back up before large edits

For risky changes:

```text
/rp backup create full
/rp backup list
```

Then verify the backup:

```text
/rp backup verify <id>
```

A backup that has never been verified is less useful than a backup you know the plugin can read safely.

## Deep pages

- [config.yml Explained](/configuration/core)
- [Integrations](/configuration/integrations)
- [Mines & Resets](/systems/mines)
- [Ranks & Prestiges](/systems/progression)
- [Selling & Boosters](/systems/economy)
- [Gangs](/systems/gangs)
