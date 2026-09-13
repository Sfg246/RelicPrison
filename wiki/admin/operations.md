# Administration, Diagnostics & Recovery

This is the page to keep open when you operate a live or staging server.

## Health commands

### Runtime status

```text
/rp status
```

Use this for the quick “is RelicPrison alive?” view.

### Configuration validation

```text
/rp validate
```

Use this after config changes and before declaring a staging build ready.

### Diagnostics

```text
/rp diagnose
/rp diagnose detail
```

The detailed path requires the sensitive diagnostic permission.

Diagnostics cover areas such as:

- database health/latency/queue/pool counters;
- dirty, failed, loading, and timed-out profiles;
- reset queue, active/failed reset state, timing;
- mining throughput/counters;
- integration names, versions, and health;
- boosters;
- incomplete/failed progression transactions;
- reward delivery state;
- failed command dispatches;
- backup state;
- configuration validation counts.

## Diagnostic export

```text
/rp diagnostic
```

The export writes a redacted ZIP under the RelicPrison diagnostics folder.

It can contain:

```text
diagnostic.txt
validation.txt
summaries/mines.txt
summaries/resets.txt
summaries/ranks.txt
summaries/prestiges.txt
logs/recent-relicprison-errors.txt
config/*.yml
REDACTION-REPORT.txt
```

The export intentionally skips/limits unsafe data such as symlinked config sources and raw database dumps.

::: warning Still review before sharing
Automated redaction lowers risk. It does not mean you should upload diagnostic archives publicly without looking at them first.
:::

## Staff audit

Query syntax:

```text
/rp audit [page] [staff=<uuid>] [action=<action>] [target=<type:id>] [from=<yyyy-mm-dd>] [to=<yyyy-mm-dd>]
```

Permission:

```text
relicprison.admin.audit
```

Audit rows record staff/system actions such as rank/prestige edits, mine admin actions, booster edits, reloads, backup operations, imports, repairs, reward operations, and diagnostic exports.

Normal player rankup/prestige actions are not treated as permanent staff-audit entries.

## Backups

### List

```text
/rp backup list
```

### Create

```text
/rp backup create full
/rp backup create config
/rp backup create data
```

### Inspect

```text
/rp backup info <id>
```

### Verify

```text
/rp backup verify <id>
```

Verification checks archive checksum/integrity, unsafe paths, duplicate entries, size limits, expected files, YAML readability, and SQLite readability where applicable.

### Delete

```text
/rp backup delete <id> confirm
```

### Restore

```text
/rp backup restore <id> confirm
```

Restore is deliberately staged. It is not a casual “overwrite live files while every service is running” command.

The restore path verifies first, creates a safety backup, writes a restore plan, stages files, and applies the pending restore during startup before normal runtime services mutate data.

## SQLite vs MySQL restore

SQLite snapshots can participate in the automated verification/restore design when valid.

Remote MySQL data restore is intentionally **not** automatically performed by RelicPrison. Full/data MySQL backups can contain logical SQL exports, but restoring remote MySQL data is an operator action.

## Progression repair

```text
/rp repair <player>
/rp repair all
```

Use repair when you have evidence that progression state needs reconciliation. Inspect first.

Progression transaction operations:

```text
/rp progression list
/rp progression info <id>
/rp progression retry <id>
```

Do not repeatedly retry an ambiguous external operation just because “retry” exists. Read its state and diagnostic context first.

## Leaderboard reward operations

```text
/rp rewards preview ...
/rp rewards finalize ...
/rp rewards history ...
/rp rewards pending ...
/rp rewards retry ...
```

The reward system uses persistent package/component state and retry behavior so period finalization is not just an untracked series of console commands.

## Data transfer

Export:

```text
/rp export <player>
```

Import:

```text
/rp import <file.yml> confirm
```

Take a backup before bulk or high-value player-data transfer operations.

## Incident order of operations

When something serious breaks:

1. **Stop making random changes.**
2. Record the exact player/mine/operation/time.
3. Run `/rp status`.
4. Run `/rp diagnose` or detail if appropriate.
5. Run `/rp validate` if configuration may be involved.
6. Query `/rp audit` for recent staff mutations.
7. Inspect progression/reset/reward operation state if relevant.
8. Create/verify a backup before destructive repair.
9. Use the narrowest supported recovery command.
10. Re-test the original scenario.

That sequence preserves evidence and gives the recovery systems a chance to do their job.
