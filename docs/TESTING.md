# Testing

## Automated Commands
- `java -version`
- `.\mvnw.cmd -version`
- `.\mvnw.cmd -B -ntp clean test`
- `.\mvnw.cmd -B -ntp clean package`

## Test Categories
- Unit tests for configuration parsing, math, utility classes, and immutable records.
- SQLite repository integration tests for migrations, progression transactions, reward ledgers, leaderboards, audit, and backup history.
- Static bytecode/source guardrails for forbidden dependencies, main-thread waits, unsafe backup patterns, and artifact contents.
- Manual staging tests for real Paper, integrations, clients, restart/crash windows, and backup restore.
- Command-presentation tests for structured help rendering, packaged prefix/color defaults, and centralized command output routing.

## Stage 23 Command UX Evidence

- Focused suite: `CommandPresentationTest` and `GangConfigAndPlaceholderTest`.
- Behavioral coverage verifies separators, categories, syntax/description styling, page counters, next-page instructions, configurable RelicPrison/Gang prefixes, and semantic value colors.
- A source guardrail verifies every class ending in `Command.java` delegates player-facing output to `MessageService` rather than calling `sendMessage` directly.
- Real Java and Bedrock client chat rendering remains a staging requirement.

## Fault-Injection Strategy
- Progression tests must inject before/after transaction insert, withdrawal intent, Vault call, withdrawal confirmation, profile save, LuckPerms update, reward package creation, and delayed command delivery.
- Reward tests verify package/component duplicate protection, replay-safe bulk money/XP recovery, repeated restart
  recovery, database reconnect recovery, and conservative `RUNNING` command ambiguity.
- Bulk SQL fault injection runs after profile progression and after mining statistics updates to prove the entire
  database transaction rolls back and replay commits each counter exactly once.
- Backup tests must verify corrupt ZIPs, traversal, absolute paths, invalid YAML, semantic config failures, and rollback.

## Evidence Locations
- Maven reports: `target/surefire-reports`, `target/failsafe-reports`.
- Build artifacts: `target/RelicPrison-*.jar`.
- Release reports: root-level `BUILD-REPORT-*`, `TEST-REPORT-*`, `MIGRATION-REPORT-*`, `KNOWN-LIMITATIONS-*`, and `STAGING-CHECKLIST-*`.

## 1.0.0 Automated Evidence

- Official command: `.\mvnw.cmd -B -ntp clean verify` on Java 25.
- Exact current Surefire, Failsafe, Checkstyle, duplicate-class, and artifact-verification results are recorded in `TEST-REPORT-1.0.0.md`.
- Startup formatting coverage verifies dynamic versioning, READY state, duration formatting, integration statuses, disabled-banner behavior, release-candidate leakage, and bounded line width.
- SQLite test JVMs receive `--enable-native-access=ALL-UNNAMED` through both Surefire and Failsafe.
- Real Paper/integration/client/manual staging remains separate and is not implied by automated success.

## Staging Matrix
Real staging must record exact versions for Paper, Vault, economy provider, LuckPerms, PlaceholderAPI, WorldEdit, FAWE where used, ItemsAdder, AdvancedEnchantments, Geyser, Floodgate, Java client, and Bedrock client. A test is pending unless the real dependency was installed and exercised.

## RC6 Stage 2 Automated Evidence

- Required stage commands run: `.\mvnw.cmd -B -ntp clean test` and `.\mvnw.cmd -B -ntp clean package`.
- Additional guardrail run: `.\mvnw.cmd -B -ntp verify`.
- Java used: Temurin `21.0.12`.
- Maven Wrapper used: Apache Maven `3.9.11`.
- Unit tests: 108 run, 0 failures, 0 errors, 0 skipped.
- Integration tests: 1 run, 0 failures, 0 errors, 0 skipped.
- New behavioral coverage includes reward component claim races, old-claim token rejection, expired claim ambiguity, progression refund claim races, Block Event trigger/package atomic creation, RC6 Stage 2 Block Event logical-claim duplicate prevention and recovery, leaderboard finalization/package atomic creation, RC6 Stage 2 leaderboard finalization recovery, duplicate leaderboard payment warnings, and RC6 Stage 1 bulk transaction state/snapshot/recovery repository behavior.
- Source-text guardrails remain secondary and are not completion proof.

## RC6 Stage 2 Manual Staging Required

- No real Paper 1.21.10 server, AdvancedEnchantments, ItemsAdder, Vault economy failure, LuckPerms failure, Java client, Bedrock/Geyser/Floodgate, restart, database outage, or forced restore rollback staging was performed in this pass.
- `RelicPrison 1.0.0-rc6-stage2` is a release candidate for controlled staging only, not a production-ready final.

## RC6 Stage 3 Automated Evidence

- Focused command run: `.\mvnw.cmd -B -ntp "-Dtest=AdminGuiEditorServiceTest,PackagedMineResourceStatusTest" test`.
- Focused tests: 10 run, 0 failures, 0 errors, 0 skipped.
- `AdminGuiEditorServiceTest` exercises all seven approved editors through the real file-backed editor service, including create/edit/toggle/delete/cancel, stale revision rejection, invalid numeric rollback, duplicate IDs, missing integration rejection, search/pagination, current-value details, reload callbacks, and audit callbacks.
- `PackagedMineResourceStatusTest` validates the intentional packaged 33-mine resource.
- Full `.\mvnw.cmd -B -ntp clean test`, `.\mvnw.cmd -B -ntp clean package`, and `.\mvnw.cmd -B -ntp clean verify` passed for Stage 3 artifact delivery.

## RC6 Stage 3 Manual Staging Required

- Real Paper 1.21.10 admin GUI editing remains pending for Java and Bedrock/Geyser clients, including simultaneous administrators, invalid input, rollback, reload persistence, and all seven editors.
- The packaged 33-mine configuration is intentional and must not be replaced.

## RC6 Stage 4 Automated Evidence

- Java used: Temurin `25.0.4.1`; Maven Wrapper: Apache Maven `3.9.11`.
- Required commands: `.\mvnw.cmd clean test` and `.\mvnw.cmd clean package`.
- Automated total: 156 tests, 0 failures, 0 errors, 0 skipped.
- Production editor tests exercise all seven file-backed editor paths, permission policy, independent/expiring input sessions, stale revisions, search/pagination, create/edit/toggle/delete/cancel, invalid values and references, rollback/reload failure, audit callbacks, custom provider validation, and main-thread marshalling.
- SQLite repository tests prove active, scheduled, disabled, and scope-changed booster instances survive repository restart.

## RC6 Stage 4 Manual Staging Required

- Real Paper 1.21.10 Java-client and Bedrock/Geyser GUI interaction is still pending.
- Vault, LuckPerms, ItemsAdder 4.0.16, AdvancedEnchantments 9.22.9, database reconnect, and forced save/reload failure staging remain required before final release.

## Unreleased Backend Recovery Evidence

- Java used: Temurin `25.0.4.1`; Maven Wrapper: Apache Maven `3.9.11`.
- `.\mvnw.cmd -B -ntp clean test` passed with 142 tests, 0 failures, 0 errors, and 0 skipped.
- New tests cover SQL crashes after progression/statistics, absolute XP and Vault target replay, repeated startup and
  reconnect recovery, rollback-versus-complete recovery decisions, mixed actual routes, partial overflow, unsellable
  and partial AutoSell outcomes, failed AutoBlock, and successful exactly-once database finalization.
- No real Paper server, Vault crash injection, AdvancedEnchantments/ItemsAdder path, offline join delivery, or database reconnect staging was performed.
- This evidence is insufficient to authorize `1.0.0-rc6-stage4`.

## RC6 Stage 5 Gang Evidence

- Focused command: `.\mvnw.cmd -B -ntp "-Dtest=GangRepositoryBehaviorTest,GangConfigAndPlaceholderTest" test`.
- Focused result: 10 tests, 0 failures, 0 errors, 0 skipped.
- SQLite behavior tests cover default and custom ranks, owner permission protection, duplicate identity rejection, invitation expiry/accept/deny, open joining, full gangs, owner leave protection, kick, atomic ownership transfer, two-database concurrent membership and bank races, upgrade spending, progression, period statistics, mission completion/claim, boosters, frozen seasons, restart persistence, historical audit retention, disband, and migration preview/confirm.
- Configuration and placeholder tests exercise immutable parsing, invalid configuration rejection, global member-limit mapping, and cache-only placeholder resolution.
- Final Java, Maven, clean test, clean package, and verify evidence is recorded in the Stage 5 root reports.

## RC6 Stage 5 Manual Staging Required

- No real Paper 1.21.10 server, Java client, Bedrock/Geyser/Floodgate client, Vault provider, LuckPerms, PlaceholderAPI, ItemsAdder, AdvancedEnchantments, or multi-server database cluster was exercised in this coding pass.
- The Stage 5 artifact is for controlled staging only and is not production-certified.

## RC6 Stage 6 GUI Evidence

- Focused command: `.\mvnw.cmd -B -ntp "-Dtest=GuiVisualsTest" test`.
- Focused result: 8 tests, 0 failures, 0 errors, 0 skipped.
- GUI behavior tests cover contextual mine/rank/prestige/booster/event/property materials, locked/unlocked states, semantic glow, colored bold names, structured actionable lore, navigation slots, pagination bounds, confirmation layouts, and gang mission/upgrade states.
- Full `.\mvnw.cmd clean test` result: 174 tests, 0 failures, 0 errors, 0 skipped.
- Final package and verify evidence is recorded in the Stage 6 root reports.

## RC6 Stage 6 Manual Staging Required

- Screenshots cannot be produced without a real Paper server and game client; representative textual layouts are recorded in `docs/GUI-DESIGN.md`.
- Every GUI still requires visual/click/sound inspection on real Paper 1.21.10 with Java and Bedrock/Geyser clients before production promotion.


