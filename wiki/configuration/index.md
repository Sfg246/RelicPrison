# Configuration Map

RelicPrison splits configuration by responsibility so one giant file does not own the entire plugin.

::: tip Complete source-generated reference
The public site now regenerates **every packaged YAML leaf** during each docs build. Use the human guides on this page to understand intent, then use [Every YAML Setting](/generated/config/) for exact paths, types, packaged defaults, recommended starting points, and risk notes.
:::

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
| `guis/*.yml` | Inventory titles/theme/presentation values; much of the inventory behavior/layout is implemented by the GUI code |

## Which file do I edit?

<div class="journey-grid">
<div class="reference-card"><strong>“I want AutoSell on.”</strong><br><br>Edit <code>config.yml</code> feature switches, then verify related selling configuration.</div>
<div class="reference-card"><strong>“Rank B costs too much.”</strong><br><br>Edit <code>ranks.yml</code>.</div>
<div class="reference-card"><strong>“Mine A has the wrong blocks.”</strong><br><br>Prefer mine admin commands for composition or edit <code>mines.yml</code> carefully.</div>
<div class="reference-card"><strong>“I want ItemsAdder blocks.”</strong><br><br>Enable the ItemsAdder bridge in <code>integrations.yml</code>, then configure supported content in the owning feature file.</div>
<div class="reference-card"><strong>“Gang member limit is wrong.”</strong><br><br>Edit <code>gangs.yml</code>.</div>
<div class="reference-card"><strong>“The GUI looks wrong.”</strong><br><br>Use the relevant <code>guis/</code> config plus the <a href="../visuals/guis">GUI Gallery</a> to see what is config-owned versus code-owned.</div>
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

```text
/rp validate
/rp diagnose
```

For staging changes, also test the exact feature you edited. Valid YAML does not prove the gameplay result is what you intended.

## Back up before large edits

```text
/rp backup create full
/rp backup list
/rp backup verify <id>
```

## Deep pages

- [Every YAML Setting](/generated/config/)
- [config.yml Explained](/configuration/core)
- [Integrations](/configuration/integrations)
- [Copy-Paste Recipes](/recipes/)
- [Mines & Resets](/systems/mines)
- [Ranks & Prestiges](/systems/progression)
- [Selling & Boosters](/systems/economy)
- [Gangs](/systems/gangs)
