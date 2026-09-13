# Stage 08

## Goal
Complete final testing, release, monitoring, and rollback preparation.

## Implemented Systems
- Release checklists.
- Monitoring checklist.
- Build/test/migration/known-limitations report process.
- RC-only status until staging passes.

## Important Files
- `docs/STAGING-PRODUCTION-ROLLBACK-MONITORING.md`
- `docs/CURRENT_STATE.md`
- `docs/TESTING.md`

## Database Migrations
- Migration 9 adds reward packages, reward components, Block Event triggers, and leaderboard winner snapshots.

## Config Migrations
- Existing config defaults are preserved; RC6 Stage 3 admin GUI editors use validated atomic file replacement with rollback.
- The packaged `mines.yml` still lacks the exact approved corrected 33-mine asset and is reported as an external release blocker rather than fabricated.
- Migration 11 adds Block Event recovery metadata and leaderboard period verification fields.

## Commands
- Build verification commands are documented in `docs/TESTING.md`.

## Permissions
- No new gameplay permission is introduced by this stage.

## Tests
- Full Maven verification plus real staging matrix.
- `AdminGuiEditorServiceTest`
- `PackagedMineResourceStatusTest`

## Exit Requirements
- `.\mvnw.cmd -B -ntp clean verify` passes and real staging signs off.

## Actual Verification Status
- Automated verification is run as part of release candidate generation.

## Remaining Limitations
- Production readiness remains pending until real staging passes.
- Final RC6 cannot be labeled complete until the exact approved 33-mine asset is supplied and validated.
