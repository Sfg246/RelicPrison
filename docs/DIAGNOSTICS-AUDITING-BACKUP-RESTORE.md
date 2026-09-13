# Diagnostics, Auditing, Backup, and Restore

This document describes the Phase 7 operational surfaces implemented for RelicPrison.

## Diagnostics

Commands:

- `/rp diagnose` shows a concise bounded status snapshot for console or staff with `relicprison.admin`.
- `/rp diagnose detail` shows the detailed snapshot and requires `relicprison.admin.diagnostic.sensitive`.
- `/rp diagnostic` preserves the existing diagnostic export workflow and creates a redacted ZIP.

The diagnostic snapshot uses live RelicPrison services and reports `unavailable` when a service cannot provide a value. Database checks run on the database executor, not on the server thread.

Reported areas include:

- Database health, latency, queue depth, and connection-pool counters.
- Dirty profiles, failed saves, loading profiles, timed-out loads, and failed loads.
- Reset queue, active resets, failed reset runtimes, retryable reset failures, slow reset slices, and placement timings.
- Mining blocks processed per tick, active operations, and subsystem performance counters.
- Vault, LuckPerms, ItemsAdder, AdvancedEnchantments, PlaceholderAPI, WorldEdit/FAWE, WorldGuard, and combat-tag integration health and versions.
- Active personal/server boosters.
- Failed and incomplete progression transactions.
- Failed reward deliveries and pending offline rewards.
- Failed Bukkit command dispatches through RelicPrison reward/hook systems.
- Backup status, last backup, and configuration validation counts.

## Diagnostic Export

`/rp diagnostic` creates `plugins/RelicPrison/diagnostics/relicprison-diagnostic-<timestamp>.zip`.

ZIP layout:

- `diagnostic.txt` — sorted key/value diagnostic snapshot.
- `validation.txt` — current configuration validation results.
- `summaries/mines.txt` — mine IDs, worlds, enabled state, volume, and composition.
- `summaries/resets.txt` — reset runtime summaries.
- `summaries/ranks.txt` and `summaries/prestiges.txt` — progression summaries.
- `logs/recent-relicprison-errors.txt` — recent RelicPrison-related server-log lines when safely readable.
- `config/*.yml` — bounded, redacted configuration files.
- `REDACTION-REPORT.txt` — files scanned, fields redacted, rules used, redaction locations, and suspicious values requiring manual review.

Export safety:

- Live source files are never modified.
- Symlinked configuration files are skipped.
- Database dumps are excluded from diagnostic exports.
- Entry names are fixed and checked for path traversal.
- Configuration files over the export size limit are skipped with an explanatory marker.
- Redaction uses key-name detection and value-pattern detection for YAML, JSON-like lines, properties, TOML-like lines, XML, connection strings, authorization headers, signed URLs, token patterns, and public IPv4 addresses.

## Staff Auditing

Audit entries are stored in `rp_staff_audit` and are not editable through normal plugin commands.

Recorded fields:

- Timestamp.
- Staff UUID and last-known name, or `system`/console when no player UUID exists.
- Action type.
- Target type and ID.
- Before and after summaries.
- Success/failure.
- Staff reason when supplied by the command path.
- Related transaction, reset, backup, reward, or operation ID when available.
- Redacted safe metadata.

Audited actions include:

- Rank and prestige administrative edits.
- Mine creation previews, confirmations, edits, composition changes, reset setting changes, movement/copy completions, deletion, forced resets, recounts, and failed-reset retries.
- Booster item grants, activations, removals, and permanent multiplier edits.
- Configuration reload success/failure.
- Backup creation, verification, deletion, and restore scheduling.
- Data imports.
- Progression repair and transaction retry.
- Leaderboard reward finalization and retry.
- Diagnostic export creation.

Normal player rankups and prestiges are not permanently audited.

Audit query:

- `/rp audit [page] [staff=<uuid>] [action=<action>] [target=<type:id>] [from=<yyyy-mm-dd>] [to=<yyyy-mm-dd>]`
- Requires `relicprison.admin.audit`.
- Queries are asynchronous and paginated.
- Date filters use UTC day boundaries for audit lookup.

## Backup Commands

Commands:

- `/rp backup list`
- `/rp backup create [full|config|data]`
- `/rp backup info <id>`
- `/rp backup verify <id>`
- `/rp backup delete <id> confirm`
- `/rp backup restore <id> confirm`

Permissions:

- `relicprison.admin.backup` for backup operations.

Backup creation flushes dirty profiles and statistics first. SQLite backups checkpoint WAL before archiving. MySQL `full` and `data` backups include a logical SQL export for RelicPrison tables; automatic MySQL restoration is intentionally not supported.

Backup metadata:

- Archive ID, type, creation time, plugin version, server version, database type, ZIP checksum, archive size, included file list, per-entry size/checksum, creation result, verification status, safety-backup relationship, and restore-history note.
- Stored as `<backup-id>.meta.properties` next to the ZIP.

## Backup Verification

`/rp backup verify <id>`:

- Verifies archive checksum.
- Reads the full ZIP to verify ZIP integrity.
- Rejects path traversal, absolute paths, drive-qualified paths, and duplicate entries.
- Enforces per-entry and total extracted-size limits.
- Validates required files for full/config backups.
- Validates readable YAML configuration entries.
- Validates SQLite database readability when a `.db` snapshot is included and SQLite JDBC is available.
- Never modifies live data.
- Saves `<backup-id>.verify.txt`.

Corrupt or unsafe archives are rejected.

## Restore Validation

`/rp backup restore <id> confirm` schedules restore only after verification succeeds. The actual restore is applied during plugin startup before runtime services start, so no active RelicPrison system mutates data during restore.

Before restore:

- Archive verification must pass.
- Paths, ZIP integrity, required files, YAML readability, and SQLite readability are checked.
- MySQL data restore is refused unless the backup is config-only.
- A safety backup is created.
- A restore plan/report is written next to the backup.

During restore:

- Files are extracted into a staging directory under `plugins/RelicPrison/backups`.
- Files are moved from staging to live paths only after validation.
- Extraction never writes directly over live files.
- Backup-directory paths are refused.

After restore:

- The server must complete restart.
- RelicPrison validates schema, configuration, ranks, prestiges, mines, runtime reset state, profiles, pending transactions, pending rewards, and LuckPerms consistency during normal startup/repair paths.
- The safety backup remains until staff deliberately deletes it.

Known limitation:

- Automatic remote MySQL restoration is not implemented by design. Restore MySQL data manually from the logical SQL export after stopping writes and confirming schema compatibility.
