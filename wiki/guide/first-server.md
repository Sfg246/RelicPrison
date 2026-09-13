# 3. First Server Setup

<div class="pathline">Setup Journey → Installation → Understand the plugin → <strong>First server setup</strong> → First mine</div>

This is the recommended order for building a brand-new RelicPrison server.

## Stage A: make storage boring

The default storage mode is SQLite:

```yaml
type: SQLITE

sqlite:
  file: player-data.db
```

For a single Paper server, SQLite is the easiest place to begin.

Do **not** switch to MySQL just because it sounds more advanced. Use MySQL when you actually need shared/multi-server database behavior or operational reasons that justify it.

### If you use MySQL

Set a real password before starting the production server:

```yaml
mysql:
  host: localhost
  port: 3306
  database: relicprison
  username: relicprison
  password: change-me
  pool-size: 6
```

<div class="danger-zone"><strong>Do not leave <code>change-me</code> in production.</strong><br>Also keep database credentials out of screenshots, public GitHub repositories, Discord logs, and diagnostic messages you post publicly.</div>

## Stage B: set the server basics

Open `config.yml`.

The current defaults include:

```yaml
server:
  timezone: America/Chicago
  excluded-worlds:
    - Spawn
    - Minigames
```

### `server.timezone`

This affects time-based behavior such as periods and scheduled systems. Set it to the timezone you actually operate in.

### `excluded-worlds`

Use this for worlds where RelicPrison should not run normal prison behavior.

## Stage C: keep advanced mining features off initially

The default feature block intentionally starts many systems disabled:

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
```

That is useful for first setup.

Test normal mining first. Then enable one feature at a time.

## Stage D: understand the progression defaults

The current starting rank is:

```yaml
progression:
  starting-rank: a
```

The packaged rank file contains A through Z. Each rank entry points to a mine ID and the money cost of advancing from that rank.

Example:

```yaml
ranks:
  A: {next-cost: 55000, mine: a}
  B: {next-cost: 68700, mine: b}
  C: {next-cost: 81100, mine: c}
```

For your first test, you only need **Mine A** to work.

## Stage E: configure integrations intentionally

The default `integrations.yml` enables the bridges for Vault, LuckPerms, WorldGuard, WorldEdit/FAWE, and PlaceholderAPI, while ItemsAdder and AdvancedEnchantments start disabled.

If a plugin is not installed, either leave the optional integration disabled or confirm RelicPrison reports the integration as unavailable without treating it as a required dependency.

Recommended first boot:

```yaml
itemsadder:
  enabled: false

advanced-enchantments:
  enabled: false
```

Add those after vanilla mining is proven.

## Stage F: create Mine A

Do not hand-write your first cuboid if you do not have to.

Use the in-game mine tools described in [Create Your First Mine](/guide/first-mine).

## Stage G: test the smallest player loop

Use one test player and verify this exact sequence:

1. Player profile loads.
2. `/mine` opens the mine menu or `/mine a` reaches Mine A.
3. Player can enter and break blocks.
4. Blocks produce expected drops.
5. `/sellvalue` reports a value for configured sellable items.
6. `/sellall` deposits the expected money.
7. Player can afford `/rankup` when their balance reaches the configured amount.
8. LuckPerms state matches the new rank.

## Stage H: test reset behavior

The current default reset engine uses:

```yaml
mine-resets:
  default-interval-seconds: 900
  default-mined-percentage: 80.0
  default-warning-seconds: [30, 10, 5, 3, 2, 1]
```

For a first test, you can force a reset with:

```text
/relicmine reset a
```

Then inspect:

```text
/relicmine info a
```

## Stage I: only then add the extras

Once the basic loop works, add features in this order:

1. PlaceholderAPI displays.
2. WorldGuard mine protection.
3. AutoPickup.
4. AutoSell.
5. AutoSmelt/AutoBlock.
6. Fortune/custom drops.
7. Block events.
8. ItemsAdder.
9. AdvancedEnchantments.
10. Gangs and leaderboards.

This order is not a hard technical requirement. It is a debugging strategy.

## First-server completion checklist

<ul class="big-checklist">
<li>☐ Storage is configured and the database opens.</li>
<li>☐ Timezone/excluded worlds make sense.</li>
<li>☐ Optional integrations match plugins actually installed.</li>
<li>☐ Mine A exists and has a spawn.</li>
<li>☐ A player can mine.</li>
<li>☐ Selling works through Vault.</li>
<li>☐ Rankup works and LuckPerms stays consistent.</li>
<li>☐ Mine reset works.</li>
<li>☐ `/rp validate` passes.</li>
<li>☐ `/rp diagnose` shows no unexplained critical failure.</li>
</ul>

## Next

Build Mine A step by step in [4. Create Your First Mine](/guide/first-mine).
