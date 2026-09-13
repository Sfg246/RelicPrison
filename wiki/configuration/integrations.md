# Integrations

RelicPrison has two kinds of integrations:

- **hard dependencies**: the plugin expects them to exist;
- **optional/soft integrations**: supported when installed and enabled, but not required for the core plugin to exist.

## Dependency map

| Integration | Default config | Role |
|---|---:|---|
| Vault | enabled | Economy bridge |
| LuckPerms | enabled | Rank/prestige permission-group synchronization |
| WorldGuard | enabled | Mine-region synchronization/protection |
| WorldEdit / FAWE | enabled | Selection/structure support |
| ItemsAdder | disabled | Custom blocks/items |
| AdvancedEnchantments | disabled | Supported enchant/bulk-mining integration |
| PlaceholderAPI | enabled | `%relicprison_...%` placeholders |
| Geyser/Floodgate awareness | enabled | Bedrock-aware behavior where supported |
| Combat provider | `none` | Optional combat-tag teleport restriction |

`plugin.yml` hard-depends on Vault and LuckPerms. WorldEdit/FAWE, PlaceholderAPI, ItemsAdder, WorldGuard, and AdvancedEnchantments are soft dependencies.

## Vault

Default:

```yaml
vault:
  enabled: true
  require-economy-provider: true
```

Vault is not the economy itself. Install an economy provider that registers through Vault.

RelicPrison uses economy operations for progression costs, selling, rewards, and gang-bank compensation paths where configured.

### Test it

1. Give a test player a known balance.
2. Run `/sellvalue` on a known sellable item.
3. Run `/sellhand` or `/sellall`.
4. Verify the Vault balance changes by the expected amount.
5. Test one `/rankup` and verify the expected withdrawal.

## LuckPerms

Default:

```yaml
luckperms:
  enabled: true
```

LuckPerms is used for managed rank/prestige group state and repair/synchronization paths.

RelicPrison gang ranks are **not** LuckPerms groups. Gang authorization is internal to the gang system.

### Test it

After one rankup:

1. inspect RelicPrison rank;
2. inspect the player's LuckPerms groups;
3. confirm the configured group format matches reality;
4. use `/rp repair <player>` only when you understand which side should be authoritative.

## WorldGuard

Current defaults:

```yaml
worldguard:
  enabled: true
  region-prefix: relicmine_
  create-regions: true
  update-regions: true
  delete-regions: true
  flags:
    deny-block-place: true
    allow-block-break: true
    deny-explosions: true
    deny-fire-spread: true
    deny-fluid-flow: true
```

A mine such as `a` can therefore map to a managed WorldGuard region using the configured prefix.

If you already manage those regions manually, decide which system owns region creation/update/deletion before enabling automatic synchronization.

## WorldEdit / FAWE

Default:

```yaml
worldedit-fawe:
  enabled: true
```

RelicPrison can use WorldEdit-compatible selection/structure provider paths where available. It also has its own mine selection wand and preview workflow.

Mine move/copy operations have recorded structure-operation recovery commands. Treat them as managed operations rather than raw block edits.

## ItemsAdder

Default:

```yaml
itemsadder:
  enabled: false
```

ItemsAdder support touches custom items/blocks in supported mine, selling, drop, event, and diagnostic paths.

### Safe enable order

1. Prove vanilla Mine A works.
2. Install/test ItemsAdder itself.
3. Enable the bridge.
4. Add one custom block/item.
5. Force one mine reset.
6. Mine the custom block.
7. Verify drop/sell/stat behavior.
8. Only then add large custom compositions.

## AdvancedEnchantments

Default:

```yaml
advanced-enchantments:
  enabled: false
```

RelicPrison includes an AdvancedEnchantments bridge through its normalized mining pipeline.

The current RC6 staging checklist still calls out real-server verification for AdvancedEnchantments behaviors such as Drill, Trench, Seismic Drill, durability, fortune, and outage behavior. Treat that integration as staging-sensitive until your own production matrix passes.

## PlaceholderAPI

Default:

```yaml
placeholderapi:
  enabled: true
```

The RelicPrison expansion identifier is:

```text
relicprison
```

Example:

```text
%relicprison_rank%
```

See [Placeholders](/reference/placeholders).

## Geyser/Floodgate awareness

```yaml
geyser-floodgate:
  awareness-enabled: true
```

This is awareness/support logic, not proof that every GUI and chat flow has been fully verified on every Bedrock client.

The current RC6 status still lists Java/Bedrock GUI and chat inspection as manual staging work.

## Combat restrictions

Default:

```yaml
combat:
  provider: none
  teleport-restriction:
    enabled: false
    bypass-permission: relicprison.bypass.combat-teleport
```

With `provider: none`, no external combat plugin is selected.

If you configure a supported provider, test mine teleport denial and the bypass permission before using it in production.

## The integration debugging rule

When an integration fails, answer these in order:

1. Is the other plugin installed?
2. Is it the expected version?
3. Did it enable before RelicPrison tries to use it?
4. Is the RelicPrison integration enabled?
5. Does `/rp diagnose` report it as available/healthy?
6. Does the simplest integration-specific test work?
7. Only then test the complex combined feature.
