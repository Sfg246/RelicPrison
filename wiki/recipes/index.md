# Copy-Paste Recipes

These are starting templates, not magic production settings. Copy only the section you understand, keep a backup, then run `/rp validate` and `/rp diagnose`.

## Recipe 1: Conservative first server

Use the packaged defaults as the baseline and keep automation-heavy features off while proving the core loop:

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
```

**Use when:** you are bringing a new server up for the first time.

## Recipe 2: OP-prison feature staging

Enable these one at a time, not as one blind paste:

```yaml
features:
  autopickup: true
  autosmelt: true
  autoblock: true
  fortune: true
  autosell: true
  mining-xp: true
  block-events: true
  player-leaderboards: true
```

**Order matters:** prove vanilla drops → pickup → transforms → selling → statistics before testing bulk enchants/custom blocks.

## Recipe 3: Mine reset profile

A safe starting pattern mirrors the packaged engine behavior:

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

Do not raise throughput simply because a reset feels slow. Watch MSPT and diagnostics first.

## Recipe 4: ItemsAdder mine block

Enable the integration:

```yaml
itemsadder:
  enabled: true
```

Then a mine composition can use the explicit provider form:

```yaml
composition:
  - block: COBBLESTONE
    weight: 90.0
  - block: itemsadder:your_namespace:your_block
    weight: 10.0
```

RelicPrison also recognizes a namespaced custom ID when it cannot be resolved as a vanilla material, but `itemsadder:` is clearer in documentation.

**Before using this:** confirm the custom block exists in ItemsAdder and test a one-block placement/mining path on staging.

## Recipe 5: AdvancedEnchantments bridge

```yaml
advanced-enchantments:
  enabled: true
```

RelicPrison’s AE integration feeds supported bulk mining into the normalized mining pipeline. Do not invent duplicate drop logic in YAML. Test Drill/Trench/Seismic-style behavior, durability, fortune, and outage behavior on the real server version you intend to run.

## Recipe 6: MySQL / multi-server starting point

```yaml
type: MYSQL

mysql:
  host: database.example.internal
  port: 3306
  database: relicprison
  username: relicprison
  password: replace-with-a-real-secret
  parameters: useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
  pool-size: 6
```

<div class="danger-zone"><strong>Do not commit the real password.</strong> The packaged <code>change-me</code> value is only a placeholder. Multi-server behavior also requires actual concurrency testing; switching to MySQL does not prove your network topology.</div>

## Recipe 7: Bedrock-aware server

```yaml
geyser-floodgate:
  awareness-enabled: true
```

This enables RelicPrison’s awareness path. It does **not** install or configure Geyser/Floodgate for you. Test every inventory, chat help page, confirmation, and gang workflow on both Java and Bedrock before calling the experience equivalent.

## Recipe 8: 33-mine packaged progression

The repository already contains an intentional packaged 33-mine configuration. Do not paste a second giant mine file over it just to get “33 mines.” Preserve the current `mines.yml`, inspect it, then modify individual mines through `/relicmine` where possible.

Recommended workflow:

```text
/relicmine list
/relicmine info a
/relicmine info z
/rp validate
```

Then make deliberate changes one mine at a time.
