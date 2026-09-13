# Migration Report - 1.0.0-rc6-stage6

- Supported schema version remains 17.
- Stage 6 introduces no database migration and no table, column, index, key, or data rewrite.
- The redesign reads existing live player, mine, progression, booster, leaderboard, gang, audit, and configuration state; it does not create a parallel GUI persistence model.
- GUI theme/title/sound defaults are file configuration only and load through the existing validated atomic configuration flow.
- Existing normalized gang tables and all prior migration guarantees remain unchanged; see `MIGRATION-REPORT-1.0.0-rc6-stage5.md` and `docs/GANGS.md`.
- The packaged 33-mine configuration is unchanged.

No database migration action is required when upgrading from Stage 5 beyond the normal startup version check. Real supported-database startup/reconnect staging remains required before production promotion.
