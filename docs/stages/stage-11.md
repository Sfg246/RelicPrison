# Stage 11

## Goal
Integration-safe mine selections and WorldEdit/FAWE compatibility.

## Implemented Systems
- Optional WorldEdit/FAWE integration status and diagnostics.

## Important Files
- `WorldEditIntegration.java`
- `DiagnosticServiceImpl.java`

## Database Migrations
- None.

## Config Migrations
- integration feature toggles.

## Commands
- mine selection/import commands where configured.

## Permissions
- mine admin permissions.

## Tests
- Source and integration availability guardrails.

## Exit Requirements
- Optional integrations can be absent.

## Actual Verification Status
- Automated absence-safety checks exist; real WorldEdit/FAWE staging remains pending.

## Remaining Limitations
- Exact WorldEdit/FAWE versions must be recorded during staging.
