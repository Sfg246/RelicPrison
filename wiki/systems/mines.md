# Mines & Resets

Mines are the physical backbone of RelicPrison. This page explains the full system after you have created at least one test mine.

## A mine definition contains

At a high level, RelicPrison needs to know:

- stable mine ID;
- display name;
- world;
- cuboid bounds;
- optional spawn;
- enabled state;
- menu sort order;
- rank/prestige/access requirements;
- metadata;
- weighted composition;
- reset configuration;
- reset hooks/runtime state.

## Selection and structure commands

| Command | Purpose |
|---|---|
| `/relicmine wand` | Give yourself the RelicPrison selector |
| `/relicmine create <mine>` | Create from the current selection |
| `/relicmine resize <mine>` | Replace bounds from a new selection |
| `/relicmine move <mine>` | Move a mine using the structure-operation path |
| `/relicmine copy <source> <new> [display name]` | Copy a mine into a new selection |
| `/relicmine delete <mine>` | Start a protected delete preview |
| `/relicmine confirm` | Confirm the pending action |
| `/relicmine cancel` | Throw away the pending action |

Create/resize/move/copy/delete use preview or protected operation paths rather than making every destructive change instantly.

## Daily management commands

```text
/relicmine list
/relicmine info <mine>
/relicmine tp <mine>
/relicmine setspawn <mine>
/relicmine enable <mine>
/relicmine disable <mine>
/relicmine rename <mine> <new-id> [display name]
/relicmine sort <mine> <order>
```

### Enabled vs accessible

These are different questions:

- **Enabled?** Is the mine generally available?
- **Accessible?** Does this specific player satisfy rank, prestige, permission, and other access checks?

A mine can be enabled while still being locked for a player.

## Access requirements

The command family is:

```text
/relicmine requirement <mine> <rank|prestige|permission|clear> <value|all>
```

Examples:

```text
/relicmine requirement diamond rank Z
/relicmine requirement immortal prestige immortal
/relicmine requirement event permission relicprison.mine.event
/relicmine requirement event clear all
```

Operators with `relicprison.bypass.mine-access` can bypass mine-access checks.

## Composition

Composition decides what blocks are placed during mine generation/reset.

Use:

```text
/relicmine composition <mine> <list|set|remove|normalize|copy>
```

A good workflow is:

1. List current weights.
2. Add or change one material.
3. Normalize weights if needed.
4. Force a reset.
5. Visually verify the result.

When ItemsAdder integration is enabled, custom blocks can participate in supported mine/drop paths. Test custom content separately from vanilla materials.

## Reset triggers

Global defaults currently include:

```yaml
mine-resets:
  default-interval-seconds: 900
  default-mined-percentage: 80.0
  empty-mine-trigger-enabled: true
```

A mine can therefore be configured around time, mined percentage, and empty-mine behavior depending on its reset settings.

## Reset performance controls

RelicPrison does not blindly place the entire mine in one server tick. The current defaults use an adaptive per-tick budget:

```yaml
mine-resets:
  target-time-per-tick-ms: 2.5
  initial-blocks-per-tick: 1000
  min-blocks-per-tick: 100
  max-blocks-per-tick: 8000
  max-concurrent-mines: 1
  pause-above-mspt: 45.0
  resume-below-mspt: 35.0
```

### In plain English

RelicPrison tries to keep reset work inside a small time budget. It can adjust how many blocks it processes per tick, pause when server tick time is unhealthy, and resume once the server recovers.

ItemsAdder placement has its own smaller default budget because custom-block work can be more expensive.

## Reset warnings and notifications

Current defaults:

```yaml
default-warning-seconds: [30, 10, 5, 3, 2, 1]
notifications:
  chat: true
  title: true
  action-bar: true
  sound: true
```

That means you can warn players in more than one presentation channel without changing reset logic.

## Forced reset, recount, and recovery

Useful commands:

```text
/relicmine reset <mine>
/relicmine recount <mine>
/relicmine retryfailed <mine>
```

Structure recovery commands:

```text
/relicmine structure list
/relicmine structure info <operation-id>
/relicmine structure retry <operation-id>
/relicmine structure rollback <operation-id>
```

Use the recovery commands when a move/copy/structure operation records a recoverable failure. Do not “fix” a half-finished operation by randomly editing files before inspecting the recorded operation.

## WorldGuard behavior

When enabled, the default integration uses region IDs beginning with:

```text
relicmine_
```

The packaged WorldGuard settings can create/update/delete regions and default to:

- deny block placement;
- allow block breaking;
- deny explosions;
- deny fire spread;
- deny fluid flow.

Review these before production if another protection plugin or custom WorldGuard design owns the same area.

## Troubleshooting a mine

### “The mine exists, but `/mine a` denies me”

Check, in order:

1. `/relicmine info a`
2. mine enabled state;
3. player's rank;
4. player's prestige;
5. custom mine permission;
6. `relicprison.mine.teleport`;
7. combat restriction;
8. `/rp diagnose` for integration/runtime issues.

### “The mine resets too slowly”

Do not immediately raise `max-blocks-per-tick` to a huge number. First check MSPT and reset diagnostics. The adaptive limits exist to protect the server thread.

### “The composition looks wrong”

List the composition, normalize it, force one reset, and verify vanilla materials before blaming an ItemsAdder bridge.
