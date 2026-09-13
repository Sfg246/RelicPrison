# Stage 02

## Goal
Persist player profile and mine data safely.

## Implemented Systems
- SQLite/MySQL repository layer.
- Profile loading and dirty flush handling.
- Mine runtime persistence.

## Important Files
- `DatabaseManager.java`
- `PlayerProfileRepository.java`
- `MineRuntimeRepository.java`

## Database Migrations
- Profile, mine runtime, migration history, and backup history tables.

## Config Migrations
- Storage configuration validation.

## Commands
- `/rp validate`
- mine commands under existing command aliases.

## Permissions
- `relicprison.admin`

## Tests
- `DatabaseInitializationTest`
- `MineRuntimeRepositoryTest`
- `PlayerProfileTest`

## Exit Requirements
- Duplicate profile loads reuse in-flight futures and shutdown flushes pending writes.

## Actual Verification Status
- Covered by automated repository tests.

## Remaining Limitations
- Database outage behavior still requires staging fault injection.
