# Backup & Restore

Backups are part of normal RelicPrison operation, not only disaster recovery. Create a verified backup before upgrades, migrations, large balance changes, mine restructuring, or database work.

## What to protect

At minimum, protect the RelicPrison plugin data directory, its database/storage data, configuration files, mine definitions, progression catalogs, gang data, reward state, and any external files required by integrations.

## Create a backup

1. Use RelicPrison's supported backup/admin operation or stop the server for a filesystem-level backup.
2. Give the backup a clear timestamp or release label.
3. Keep it outside the live plugin directory.
4. Record the plugin version, Paper version, Java version, and relevant integration versions.
5. Run verification before considering the backup safe.

## Verify a backup

A backup that has never been restored is unproven. On a staging server:

1. Start from a clean compatible server.
2. Restore the backup.
3. Start Paper and inspect all RelicPrison startup diagnostics.
4. Join with test players representing different ranks/prestiges/gangs.
5. Confirm mines, balances, progression, active boosters, statistics, and reward state.
6. Run a mine reset and one normal progression action.
7. Record the result.

## Restore procedure

1. Stop the destination server completely.
2. Create a final snapshot of the current broken state in case it is needed for investigation.
3. Restore the matching RelicPrison files/database.
4. Confirm permissions and ownership of restored files.
5. Start in restricted/staging access.
6. Check migration and schema logs before allowing players to join.
7. Run smoke tests.
8. Reopen access only after the state is confirmed.

## Never do this

Do not mix a database from one point in time with configuration from another unless the migration procedure explicitly requires it. Do not overwrite a live database while Paper is still writing to it. Do not delete failed reward/transaction state simply to make an error disappear.

## Disaster checklist

If production breaks, stop writes first, preserve evidence/logs, identify the last known-good backup, restore to staging, validate, then promote the known-good state.

## Related pages

- [Upgrade & Migration Center](/upgrade/)
- [Admin Operations](/admin/operations)
- [Known Limitations](/known-limitations)
- [Troubleshooting](/troubleshooting/)
