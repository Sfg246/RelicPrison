# Stage 13

## Goal
Economy, LuckPerms, and profile repair hardening.

## Implemented Systems
- Vault adapter.
- LuckPerms replacement/repair helpers.
- Progression recovery on join.

## Important Files
- `VaultEconomyAdapter.java`
- `LuckPermsIntegration.java`
- `ProgressionJoinListener.java`

## Database Migrations
- progression transaction tables.

## Config Migrations
- progression settings.

## Commands
- `/rp repair`
- `/rp progression`

## Permissions
- repair/admin permissions.

## Tests
- `VaultEconomyAdapterTest`
- `LuckPermsIntegrationTest`

## Exit Requirements
- Failure states are surfaced and not silently retried unsafely.

## Actual Verification Status
- Adapter-level tests exist. Real Vault/LuckPerms failure staging remains required.

## Remaining Limitations
- Generic Vault ambiguity requires staff investigation.
