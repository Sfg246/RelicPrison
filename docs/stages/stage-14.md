# Stage 14

## Goal
Performance counters and operational diagnostics before gameplay expansion.

## Implemented Systems
- Low-overhead performance metrics.
- Diagnostics include queue, reset, integration, command, and backup status.

## Important Files
- `PerformanceMetrics.java`
- `DiagnosticServiceImpl.java`

## Database Migrations
- None.

## Config Migrations
- diagnostics/export defaults.

## Commands
- `/rp diagnose`
- `/rp diagnostic`

## Permissions
- `relicprison.admin.diagnostic.sensitive`

## Tests
- `PhaseSevenEightSourceTest`

## Exit Requirements
- Diagnostics use real service state where available.

## Actual Verification Status
- Automated redaction and service-reference checks exist.

## Remaining Limitations
- RelicProfiler/JFR profiling must be performed on staging load.
