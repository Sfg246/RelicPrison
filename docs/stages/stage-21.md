# Stage 21 - Native Prison Gangs

## Completed

- Added a database-authoritative native gang model without land, power, raids, or territory mechanics.
- Added persistent membership, invitations, default/custom ranks, rank permissions, safe ownership transfer, and confirmed disband.
- Added an atomic gang bank ledger, daily withdrawal limits, configurable upgrades, progression, committed contributions, missions, and gang-scoped boosters through the existing booster service.
- Added gang chat, player/admin commands, GUI sections, custom-rank editing, PlaceholderAPI values, staff auditing, reconnect refresh, leaderboards, seasons, and reward-ledger recovery.
- Added schema migration 17 and two-database behavioral coverage for membership and withdrawal races.

## Automated Evidence

- Focused gang suite: 10 tests, 0 failures, 0 errors, 0 skipped.
- Final clean test/package/verify evidence is recorded in the Stage 5 release reports.

## Staging Boundary

- Real Paper 1.21.10, Java/Bedrock clients, Vault/LuckPerms/PlaceholderAPI, optional integrations, database reconnect, and two-server concurrency staging remain pending.
- This stage authorizes only an RC staging JAR, not final `1.0.0` production status.
