# `config.yml` Explained

This page walks through the current packaged `config.yml` in normal language.

## `server`

```yaml
server:
  timezone: America/Chicago
  excluded-worlds:
    - Spawn
    - Minigames
```

### `timezone`

Used by time-based systems and period logic. Use a valid Java/IANA timezone such as `America/Chicago`, not a made-up abbreviation like `CST` if you want daylight-saving behavior handled correctly.

### `excluded-worlds`

World names where normal prison behavior should be excluded.

## `startup`

```yaml
startup:
  banner: true
  environment-details: true
  integration-summary: true
```

| Setting | Meaning |
|---|---|
| `banner` | Prints the branded READY presentation once after successful initial enable |
| `environment-details` | Includes live Paper, Minecraft, Java, OS, and storage information |
| `integration-summary` | Includes ENABLED, DISABLED, NOT INSTALLED, and UNAVAILABLE integration states |

Reloading configuration does not print the full startup banner again. Existing servers missing this section use safe `true` defaults.

## `features`

```yaml
features:
  autosell: false
  autopickup: false
  autosmelt: false
  autoblock: false
  fortune: false
  custom-block-drops: false
  mining-xp: false
  block-events: false
  player-leaderboards: false
  player-mining-statistics: true
  mine-analytics: false
  multiple-currencies: false
  reset-command-hooks: true
  update-checker: false
```

These are global feature gates.

A feature gate being `true` means the system is allowed to operate. It does **not** guarantee every related per-feature file is correctly configured.

Example: turning `block-events` on does not magically invent a valid event configuration. `block-events.yml` still owns the actual events.

## `selection`

```yaml
selection:
  session-timeout-seconds: 300
  preview-refresh-ticks: 20
  preview-max-particles: 600
  preview-spacing: 2
  maximum-volume: 250000
  allow-overlap: false
  wand-material: BLAZE_ROD
```

| Setting | Meaning |
|---|---|
| `session-timeout-seconds` | How long an admin selection/preview session can sit before expiring |
| `preview-refresh-ticks` | How often selection preview particles refresh |
| `preview-max-particles` | Safety cap for preview particles |
| `preview-spacing` | Distance between preview samples/particles |
| `maximum-volume` | Largest allowed selected mine volume through this path |
| `allow-overlap` | Whether mine selections may overlap existing mine bounds |
| `wand-material` | Material used by `/relicmine wand` |

## `mine-resets`

```yaml
mine-resets:
  target-time-per-tick-ms: 2.5
  initial-blocks-per-tick: 1000
  min-blocks-per-tick: 100
  max-blocks-per-tick: 8000
  max-concurrent-mines: 1
  empty-mine-trigger-enabled: true
  pause-above-mspt: 45.0
  resume-below-mspt: 35.0
  default-interval-seconds: 900
  default-mined-percentage: 80.0
  default-warning-seconds: [30, 10, 5, 3, 2, 1]
```

### Performance budget

`target-time-per-tick-ms` is the reset engine's target slice budget. The engine can adjust block throughput between the configured min/max range.

Do not treat `max-blocks-per-tick` as a score to maximize. A giant value can hurt server tick time.

### Pause/resume thresholds

- `pause-above-mspt`: reset work can pause when server tick time is unhealthy.
- `resume-below-mspt`: reset work can resume after health recovers.

Using separate pause/resume values helps prevent constant start/stop flapping around one threshold.

### Default triggers

- `default-interval-seconds: 900` = 15 minutes.
- `default-mined-percentage: 80.0` = default percentage-based reset threshold.
- `empty-mine-trigger-enabled: true` allows the empty-mine path.

New mines created through the admin flow take default reset values from this configuration.

### ItemsAdder reset budget

Custom block placement has its own smaller timing/throughput settings under:

```yaml
mine-resets:
  itemsadder:
    target-time-per-tick-ms: 1.0
    initial-blocks-per-tick: 100
    min-blocks-per-tick: 10
    max-blocks-per-tick: 1000
```

Keep it conservative until you have real MSPT measurements.

## `mine-access`

```yaml
mine-access:
  allow-previous-mines: true
  enforce-entry: true
```

### `allow-previous-mines`

Controls whether progressed players can continue using earlier mines when the rest of their access conditions permit it.

### `enforce-entry`

Controls active entry enforcement for mine access rather than only checking the `/mine` teleport command.

## `teleport`

```yaml
teleport:
  warmup-seconds: 3
  cancel-on-move: true
  cancel-on-damage: true
```

This creates a short delay before teleport completion and can cancel that teleport if the player moves or takes damage.

Bypasses include:

```text
relicprison.bypass.teleport-warmup
relicprison.bypass.combat-teleport
```

## `first-join`

```yaml
first-join:
  teleport-to-starting-mine: true
  commands: []
```

Commands here run once for a brand-new profile. Supported placeholders in the packaged comment are:

```text
%player%
%uuid%
%rank%
```

Keep first-join command lists small and test them with a genuinely new profile.

## `progression`

```yaml
progression:
  starting-rank: a
  rank-group-format: '{rank}'
  prestige-group-format: '{prestige}'
  broadcast-rankups: false
  broadcast-prestiges: true
  prestige-confirmation-seconds: 30
  repair-on-join: true
```

### Group formats

These tell RelicPrison how its progression IDs map to LuckPerms group names.

If Rank A should map to a group named `rank-a`, change the format intentionally and stage-test it. Do not change the pattern on a live server without understanding how existing users will reconcile.

### `repair-on-join`

Allows the join path to repair/align progression state where supported.

## `logging`

```yaml
logging:
  debug: false
  categories:
    startup: false
    config: false
    database: false
    mines: false
    reset: false
    progression: false
    economy: false
    selling: false
    mining: false
    integration: false
    migration: false
    backup: false
    diagnostic: false
```

Use category logging to investigate one subsystem instead of turning every debug source on at the same time.

## `formatting`

```yaml
formatting:
  currency-symbol: '$'
  decimals: 2
  abbreviated-decimals: 2
  use-grouping: true
  abbreviations: [K, M, B, T, Qa, Qi, Sx, Sp, Oc, No, Dc]
```

This affects RelicPrison's number presentation, not your Vault provider's underlying balance precision.

The progress bar settings control visual formatting:

```yaml
progress-bar:
  length: 10
  filled: '█'
  empty: '░'
```

## `placeholders`

```yaml
placeholders:
  value-cache-millis: 250
  leaderboard-cache-millis: 30000
  slow-threshold-millis: 10
  unavailable-text: loading
  malformed-text: invalid
```

Placeholder evaluation is designed to use cached/read-only paths rather than synchronous SQL.

- `unavailable-text` is returned when data is temporarily unavailable.
- `malformed-text` is used when a dynamic placeholder request is invalid.
- slow evaluation counters are exposed through diagnostics.

## `gui.security`

```yaml
gui:
  security:
    click-cooldown-millis: 250
    session-timeout-seconds: 120
    confirmation-timeout-seconds: 15
```

These settings protect interactive GUI operations from spam, stale sessions, and long-lived destructive confirmations.

## `leaderboards`

```yaml
leaderboards:
  refresh-seconds: 60
  snapshot-size: 100
  page-size: 45
```

Leaderboards are served from asynchronously refreshed cached snapshots. The placeholder path does not perform live SQL every time a scoreboard asks for a value.

### Seasons

```yaml
season:
  enabled: false
  id: season-1
  start: '2026-01-01T00:00:00Z'
  end: '2027-01-01T00:00:00Z'
```

Do not turn seasons on until the ID and time window are deliberate.

### Reward safety

```yaml
rewards:
  retry-limit: 3
  command-limit-per-period: 128
  announcement-cooldown-seconds: 30
```

These settings bound retries, external command volume, and announcements.

## After editing `config.yml`

Use:

```text
/rp reload
/rp validate
/rp diagnose
```

If the change touches startup-only concerns, dependencies, database topology, or restore state, restart Paper instead of forcing a reload.
