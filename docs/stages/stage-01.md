# Stage 01

## Goal
Establish the Paper plugin baseline, Maven build, resources, and public command entry points.

## Implemented Systems
- Java 21 Maven project.
- Paper `plugin.yml` filtering.
- Core `/rp` command shell and startup lifecycle.

## Important Files
- `pom.xml`
- `src/main/resources/plugin.yml`
- `src/main/java/site/mcrelicworld/relicprison/RelicPrisonPlugin.java`

## Database Migrations
- Initial schema foundations are in `DatabaseManager`.

## Config Migrations
- Default YAML resources are copied by the plugin.

## Commands
- `/rp status`
- `/rp reload`

## Permissions
- `relicprison.admin`
- `relicprison.admin.reload`

## Tests
- Build and resource guardrails.

## Exit Requirements
- Java 21 build compiles against Paper 1.21.10.

## Actual Verification Status
- Automated verification is required through `.\mvnw.cmd -B -ntp clean verify`.

## Remaining Limitations
- Real Paper startup remains a manual staging requirement.
