# Troubleshooting

Start with the symptom, not with random settings.

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
4. Are the required/optional integrations available as expected?
5. Is the problem reproducible with one simple test?

## Plugin will not start

### Check Java

The current build requires Java 25.

```bash
java -version
```

If GitHub/your host reports Java 21, you are using the wrong runtime for this build.

### Check Paper

Current target:

```text
Paper 1.21.10
```

### Check hard dependencies

Make sure these load:

```text
Vault
LuckPerms
```

Also make sure Vault has a real economy provider.

### Check database/config startup errors

Do not delete the database because a startup message looks scary. Capture the exact error and inspect it first.

## `/rankup` fails

Check:

```text
1. Current RelicPrison rank
2. Configured next rank exists
3. Vault balance
4. Vault provider health
5. Rank cost / prestige rank-cost multiplier
6. LuckPerms health
7. Progression transaction state
```

Useful commands:

```text
/relicrank info <player>
/rp diagnose
/rp progression list
```

## Player cannot enter/teleport to a mine

Check:

```text
/relicmine info <mine>
```

Then verify:

- mine is enabled;
- player has `relicprison.mine.teleport`;
- player meets rank requirement;
- player meets prestige requirement;
- player meets custom permission requirement;
- combat teleport restriction is not denying them;
- you are not accidentally testing with an op/bypass and assuming normal players behave the same.

## Mine will not reset

Check:

1. mine exists/enabled;
2. current reset state;
3. percentage/interval configuration;
4. reset queue/concurrency;
5. MSPT pause condition;
6. failed reset record/retry state;
7. ItemsAdder placement integration if custom blocks are involved.

Commands:

```text
/relicmine info <mine>
/relicmine reset <mine>
/relicmine recount <mine>
/relicmine retryfailed <mine>
/rp diagnose
```

## Mine reset is laggy

Do **not** immediately make `max-blocks-per-tick` enormous.

The reset engine already has timing, min/max throughput, concurrency, and pause/resume controls. Use diagnostics/MSPT evidence before tuning.

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

Useful comparisons:

```text
/sellvalue hand
/sellhand
```

Test one known vanilla item before testing bulk/custom/enchanted mining.

## Placeholder says `loading`

That can be intentional.

The current config uses:

```yaml
unavailable-text: loading
malformed-text: invalid
```

A player profile, leaderboard snapshot, or other cached value may not be ready yet.

If it never becomes available, check PlaceholderAPI registration and `/rp diagnose`.

## Placeholder says `invalid`

The placeholder request is malformed or references an invalid dynamic target.

Examples:

- invalid material name in `blocks_material_*`;
- unknown mine ID in a dynamic mine request;
- unknown leaderboard board;
- malformed position suffix.

## ItemsAdder problem

First prove the same mine works with vanilla blocks.

Then verify:

1. ItemsAdder itself is healthy;
2. integration is enabled;
3. custom ID exists;
4. reset placement works for one block type;
5. mining/drop behavior works;
6. selling works separately if expected.

## AdvancedEnchantments problem

Test vanilla mining first.

The current RC staging status still requires real-server verification of several AdvancedEnchantments behaviors. Capture the exact enchant, level, tool, block layout, number of blocks affected, and console output.

## Gang bank/member problem

Use the gang's stable identity/name/tag carefully and inspect:

```text
/gang bank history
/relicgang inspect <gang>
/relicgang audit <gang>
```

For bank issues, remember Vault and the gang SQL transaction cannot be one physical transaction. Compensation/reconciliation paths exist, but crash ambiguity still deserves operator inspection.

## “I changed YAML and nothing happened”

Possible reasons:

- wrong file;
- wrong live server directory;
- change requires `/rp reload`;
- change requires restart;
- invalid configuration was rejected;
- feature gate is disabled;
- another per-feature setting still blocks it;
- you edited the source repo copy, not the live `plugins/RelicPrison/` copy.

## When to create a diagnostic ZIP

Use:

```text
/rp diagnostic
```

when a problem crosses several systems or you need a redacted package for developer/staff investigation.

Review the archive before sharing it.
