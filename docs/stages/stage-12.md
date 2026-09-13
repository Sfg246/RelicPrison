# Stage 12

## Goal
Reload rollback and configuration validation hardening.

## Implemented Systems
- Atomic reload preview/apply/rollback pipeline.
- Cross-file validation service.

## Important Files
- `RelicPrisonPlugin.java`
- `ValidationService.java`
- `ReloadValidationException.java`

## Database Migrations
- None.

## Config Migrations
- validation handles existing YAML defaults.

## Commands
- `/rp reload`
- `/rp validate`

## Permissions
- `relicprison.admin.reload`

## Tests
- configuration validator tests.

## Exit Requirements
- Invalid reload does not leave partial runtime state.

## Actual Verification Status
- Automated config tests exist; live reload during reset/mining remains staging.

## Remaining Limitations
- Plugin-manager reload behavior on production servers is not fully reproducible in unit tests.
