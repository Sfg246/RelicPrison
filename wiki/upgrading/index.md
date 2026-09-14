# Upgrade, Migration & Rollback Center

Use this page whenever you replace an existing RelicPrison build. The safest upgrade is not “drop in the new JAR and hope.” It is **backup → compare → stage → verify → promote**.

## The safe upgrade path

### 1. Record your current state

Before changing anything, record:

- current RelicPrison version;
- Paper and Java versions;
- installed integrations and their versions;
- storage/database mode;
- custom mine/rank/prestige/gang configuration;
- custom GUI/message files;
- current player count and maintenance window.

### 2. Create and verify a backup

Create a RelicPrison backup and also use your normal server/host backup process. A backup is not trusted until you can verify it exists and can be read.

### 3. Read the changelog and limitations

Open [Changelog](/changelog/) and [Known Limitations](/known-limitations). Look specifically for schema changes, new defaults, behavior changes, integration requirements and manual staging requirements.

### 4. Compare configuration files

RelicPrison does **not** blindly overwrite established configuration files on upgrade. This protects your settings, but it means new defaults may not appear automatically.

Pay special attention to:

- `config.yml`
- `integrations.yml`
- `mines.yml`
- `ranks.yml`
- `prestiges.yml`
- `mining.yml`
- `boosters.yml`
- `block-events.yml`
- `gangs.yml`
- `leaderboards.yml`
- `leaderboard-rewards.yml`
- `messages.yml`
- `guis/*.yml`

Use the [generated YAML reference](/generated/config/) as the current packaged truth.

### 5. Upgrade staging first

Copy production data into an isolated staging environment. Upgrade there before touching the live server.

### 6. Start once and read the entire startup log

Do not judge success only by “server started.” Look for configuration validation failures, migration messages, missing integrations, database errors and recovery warnings.

### 7. Run an acceptance matrix

At minimum verify:

1. player join/profile load;
2. mine teleport/access;
3. ordinary mining;
4. bulk mining if used;
5. AutoPickup/AutoSell/AutoBlock;
6. rank up and prestige;
7. Vault/LuckPerms integration;
8. ItemsAdder/AdvancedEnchantments if enabled;
9. gangs and gang bank if used;
10. leaderboards/rewards;
11. admin GUI editors;
12. backups/diagnostics;
13. Java client;
14. Bedrock/Geyser client if supported.

### 8. Promote during a maintenance window

Stop the production server cleanly, take a final backup, deploy the exact staged artifact/config set, then start and repeat the critical smoke tests.

## Database/schema migrations

RelicPrison includes schema migrations as the backend evolves. Never copy only selected database tables from a newer schema into an older build. Keep the plugin build, database state and configuration snapshot together when testing rollback.

## Rollback procedure

If the upgraded build fails acceptance:

1. Stop the server immediately.
2. Preserve the failed-upgrade logs and database/config state for diagnosis.
3. Do **not** keep letting players generate new data while deciding what to do.
4. Restore the complete pre-upgrade snapshot, not only the JAR.
5. Restore matching configuration and database files.
6. Start the old version in staging/maintenance mode first.
7. Verify player profiles, mines, balances, progression and gangs.
8. Reopen the server only after the restored state is coherent.

::: danger JAR-only rollback can be unsafe
If the new build migrated persistent data, replacing only the JAR may leave the old plugin reading a newer schema or newer configuration state.
:::

## Existing-server GUI note

Stage 6 introduced/expanded shared GUI defaults. Existing `guis/*.yml` files are not overwritten automatically. Merge new theme/menu defaults deliberately or regenerate missing files in a controlled way.

## Version-specific guidance

- [RC6 Stage 6 status](/releases/rc6)
- [Compatibility Matrix](/reference/compatibility)
- [Versioned Documentation](/versions/)
- [Admin & Recovery](/admin/operations)
