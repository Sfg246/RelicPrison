# Migration Report - 1.0.0

- Supported schema version remains 17.
- The 1.0.0 release-polish pass adds no database migration, table, column, index, key, or data rewrite.
- Startup presentation configuration is file-only and uses safe defaults for existing servers missing the new section.
- Existing player, mine, progression, booster, leaderboard, gang, audit, recovery, and backup behavior is unchanged.
- The packaged 33-mine configuration and all prior migration guarantees remain unchanged.

No database migration action is required when upgrading from `1.0.0-rc6-stage6` beyond the normal startup schema check. Real supported-database startup and reconnect staging remains pending.
