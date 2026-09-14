# Staging Checklist - 1.0.0

## Manual Verification Still Required

- [ ] Start, stop, reload configuration, reconnect the database, and restart on Paper 1.21.10 with Java 25.
- [ ] Confirm the startup banner appears once, only after READY, and accurately reflects installed/disabled/unavailable integrations.
- [ ] Confirm startup failure retains its stack trace, displays no READY banner, and disables safely.
- [ ] Confirm SQLite startup uses `--enable-native-access=ALL-UNNAMED` on Java 25 without a native-access warning.
- [ ] Render every inventory listed in `docs/GUI-DESIGN.md` on Java and Bedrock/Geyser clients.
- [ ] Verify click routing, titles/lore, sounds, custom-model/resource-pack behavior, and protected confirmations.
- [ ] Exercise Vault, LuckPerms, PlaceholderAPI, ItemsAdder, AdvancedEnchantments, WorldEdit/FAWE, WorldGuard, and configured combat integration paths.
- [ ] Exercise backup restore, rollback, database outage/reconnect, restart/crash recovery, and offline delivery.
- [ ] Exercise simultaneous gang and economy mutations on two real Paper servers sharing a supported database.
- [ ] Confirm existing mining, progression, selling, resets, statistics, leaderboards, boosters, gangs, GUIs, APIs, and recovery behavior.

Automated checks pass. Every unchecked item above remains pending until observed and recorded on the real server/client/integration combination.
