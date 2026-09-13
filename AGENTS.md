# RelicPrison Agent Guide

## Repository Structure
- `src/main/java/site/mcrelicworld/relicprison`: Paper plugin source.
- `src/main/resources`: plugin YAML, defaults, GUI/menu config, and runtime examples.
- `src/test/java`: unit, repository, static guardrail, and limited behavioral tests.
- `docs`: release, staging, architecture, and operator documentation.

## Build Commands
- Use Java 21 only.
- Verify Java: `java -version`.
- Verify Maven wrapper: `.\mvnw.cmd -version`.
- Full verification: `.\mvnw.cmd -B -ntp clean verify`.

## Coding Rules
- Keep Maven and the Paper `1.21.10-R0.1-SNAPSHOT` API.
- Preserve public command, permission, config, database, and API compatibility where practical.
- Do not add fake Bukkit, Paper, Vault, LuckPerms, PlaceholderAPI, ItemsAdder, AdvancedEnchantments, WorldEdit, FAWE, Geyser, or Floodgate classes.
- Optional integrations must remain optional and must not load integration classes when absent.

## Threading Rules
- Do not perform SQL, file I/O, future waiting, YAML saving, or backup work on the Paper server thread.
- Do not call Bukkit APIs asynchronously unless the API explicitly supports it.
- Mining hot paths must not perform SQL or parse configuration per block.

## Integration Rules
- Keep integration-specific event and reflection handling at integration boundaries.
- Core mining operates on normalized operations and must preserve the fast vanilla path.
- Do not repeatedly reflect inside per-block loops.

## Database Migration Rules
- Add ordered, idempotent migrations in `DatabaseManager`.
- Preserve data and use unique constraints for duplicate protection.
- Failed migrations must stop startup safely.
- Update `docs/MIGRATION-REPORT-*` and `docs/stages/*` when schema changes.

## Testing Expectations
- Prefer behavioral tests over source-text existence checks.
- Add repository/fault-injection tests for any external side-effect boundary.
- Separate automated tests from real Paper/integration/manual staging evidence.

## Required Documentation Updates
- Update `README.md`, `CHANGELOG.md`, `docs/CURRENT_STATE.md`, `docs/TESTING.md`, and the affected `docs/stages/stage-*.md` after every stage-level change.
- Build and release passes must update a fresh build report, test report, migration report, staging checklist, and known-limitations report.

## Prohibited Shortcuts
- Do not mark a requirement complete because a class, method, or string exists.
- Do not call the plugin production-ready unless automated verification and real staging pass.
- Do not include credentials, build output, IDE files, temporary databases, server worlds, downloaded JDKs, or integration plugin jars in source archives.
- Do not fabricate missing approved configuration assets such as mine coordinates; report the missing asset as a release blocker.

## Release Procedure
- Run Java/Maven version checks and `.\mvnw.cmd -B -ntp clean verify`.
- Generate fresh artifacts, reports, and SHA-256 checksums from the final source.
- Use an RC version until Paper 1.21.10 staging, Java client, Bedrock client, Vault, LuckPerms, ItemsAdder, AdvancedEnchantments, backup restore, and rollback tests pass.
