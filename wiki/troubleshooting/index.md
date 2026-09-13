# Troubleshooting

Start with the symptom, not with random settings.

::: tip Search the exact error first
The new [Error Encyclopedia](/troubleshooting/errors) maps common startup, mine, database, integration, placeholder, reset, backup, and gang failures to a safe fix order.
:::

## Universal first five checks

Run:

```text
/rp status
/rp validate
/rp diagnose
```

Then answer:

1. Did Paper finish starting normally?
2. Did RelicPrison enable?
3. Is the database healthy?
4. Are required/optional integrations available as expected?
5. Is the problem reproducible with one simple test?

## Plugin will not start

### Check Java

The current build requires Java 25.

```bash
java -version
```

If GitHub/your host reports Java 21, you are using the wrong runtime for this build.

### Check Paper

Current target: `Paper 1.21.10`.

### Check hard dependencies

Make sure Vault and LuckPerms load, and make sure Vault has a real economy provider.

### Check database/config startup errors

Do not delete the database because a startup message looks scary. Capture the exact error and inspect it first.

## `/rankup` fails

Check current RelicPrison rank, configured next rank, Vault balance/provider, rank cost/prestige multiplier, LuckPerms health, and progression transaction state.

```text
/relicrank info <player>
/rp diagnose
/rp progression list
```

## Player cannot enter/teleport to a mine

```text
/relicmine info <mine>
```

Verify the mine is enabled, the player has `relicprison.mine.teleport`, rank/prestige/custom permission requirements are met, combat restriction is not denying them, and you are not accidentally testing only as an op/bypass user.

## Mine will not reset

Check mine state, percentage/interval configuration, queue/concurrency, MSPT pause condition, failed reset state, and ItemsAdder placement if custom blocks are involved.

```text
/relicmine info <mine>
/relicmine reset <mine>
/relicmine recount <mine>
/relicmine retryfailed <mine>
/rp diagnose
```

## Mine reset is laggy

Do **not** immediately make `max-blocks-per-tick` enormous. Use diagnostics/MSPT evidence before tuning the existing timing, throughput, concurrency, and pause/resume controls.

## Selling gives the wrong amount

Trace the pipeline:

```text
raw block
→ raw drops
→ fortune/custom drops
→ smelt/block conversion
→ sellable quantity
→ base price
→ multiplier/boosters
→ Vault deposit
```

Compare `/sellvalue hand` with `/sellhand` using one known vanilla item before testing bulk/custom/enchanted mining.

## Placeholder says `loading`

That can be intentional. Cached profile/leaderboard data may not be ready yet. If it never resolves, inspect PlaceholderAPI registration, database health, and `/rp diagnose`.

## Placeholder says `invalid`

The request is malformed or references an invalid material, mine, leaderboard board, position, or other dynamic target.

## ItemsAdder problem

First prove the same mine works with vanilla blocks. Then verify ItemsAdder itself, integration enablement, custom ID, one-block reset placement, mining/drop behavior, and selling separately.

## AdvancedEnchantments problem

Test vanilla mining first. The current RC still requires real-server verification of important AE behaviors. Capture enchant, level, tool, block layout, affected block count, durability, fortune, and console output.

## Gang bank/member problem

```text
/gang bank history
/relicgang inspect <gang>
/relicgang audit <gang>
```

Remember that Vault and the gang SQL transaction cannot be one physical transaction; hard-crash ambiguity deserves operator inspection.

## “I changed YAML and nothing happened”

Possible reasons include wrong live folder, restart/reload requirement, invalid configuration rejected, disabled feature gate, another prerequisite, or editing the source-repo copy rather than `plugins/RelicPrison/`.

Use [Every YAML Setting](/generated/config/) to confirm the current packaged path/default.

## When to create a diagnostic ZIP

```text
/rp diagnostic
```

Use it when a problem crosses several systems or needs developer/staff investigation. Review the redacted archive before sharing it.
