# Known Limitations - 1.0.0

- The exact RelicPrison 1.0.0 release JAR has now been startup-tested on Paper 1.21.5 through 26.2 with Java 25. This verifies loading, required dependency connection, core/database initialization, and the READY state, not full feature-by-feature regression testing on every version.
- Paper 1.21.4 does not pass the compatibility probe because its plugin remapper cannot process the Java 25 class format used by this release. Paper 1.21.4 and older are not advertised as supported.
- The READY-only startup presentation has been observed in automated Paper boot probes, but real operator console environments and every optional integration-state combination have not been manually staged.
- Java 25 servers using SQLite should start Paper with `--enable-native-access=ALL-UNNAMED`; a plugin cannot grant native access after JVM launch.
- GUI proportions, title/lore clipping, click routing, sound balance, custom resource-pack behavior, and protected confirmation flows still require real client staging.
- Existing configuration files are not overwritten on upgrade. Established servers must merge the new `startup` defaults deliberately if they want to customize them.
- Generic Bukkit/third-party commands cannot prove exactly-once completion when the receiver exposes no idempotency key or transaction participation.
- Paper inventory and dropped-entity persistence remains outside the SQL transaction; hard process-loss windows require real crash staging.
- Vault cannot participate in gang-bank SQL transactions; the documented hard-crash ambiguity still requires operator reconciliation testing.
- Multi-server gang, database outage/reconnect, backup restore, rollback, ItemsAdder, AdvancedEnchantments, optional-integration combinations, and Java/Bedrock/Geyser client verification remain pending.

RelicPrison 1.0.0 is the official final version. This version label does not claim that the unperformed environment-specific staging items above passed.
