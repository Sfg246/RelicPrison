# Known Limitations - 1.0.0

- No real Paper 1.21.10 server or Java/Bedrock/Geyser client was available during this release-polish pass.
- The new READY-only startup presentation is unit-tested but still requires real Paper console rendering and integration-state verification.
- Java 25 servers using SQLite should start Paper with `--enable-native-access=ALL-UNNAMED`; a plugin cannot grant native access after JVM launch.
- GUI proportions, title/lore clipping, click routing, sound balance, custom resource-pack behavior, and protected confirmation flows still require real client staging.
- Existing configuration files are not overwritten on upgrade. Established servers must merge the new `startup` defaults deliberately if they want to customize them.
- Generic Bukkit/third-party commands cannot prove exactly-once completion when the receiver exposes no idempotency key or transaction participation.
- Paper inventory and dropped-entity persistence remains outside the SQL transaction; hard process-loss windows require real crash staging.
- Vault cannot participate in gang-bank SQL transactions; the documented hard-crash ambiguity still requires operator reconciliation testing.
- Multi-server gang, database outage/reconnect, backup restore, rollback, ItemsAdder, AdvancedEnchantments, Vault, LuckPerms, and client verification remains pending.

RelicPrison 1.0.0 is the official final version. This version label does not claim that the unperformed environment-specific staging items above passed.
