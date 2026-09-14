# Test Report - 1.0.0

## Environment

- Date: 2026-09-14 (America/Chicago)
- Java: Eclipse Temurin `25.0.4.1`
- Maven Wrapper: Apache Maven `3.9.16`
- Paper API target: `1.21.10-R0.1-SNAPSHOT`
- Command: `.\mvnw.cmd -B -ntp clean verify`
- Result: `BUILD SUCCESS`

## Automated Results

- Surefire: 188 tests, 0 failures, 0 errors, 0 skipped.
- Failsafe: 1 test, 0 failures, 0 errors, 0 skipped.
- Checkstyle: 0 violations.
- Duplicate-class verification: PASS for compile, runtime, and test classpaths.
- Artifact verification: PASS; the production JAR contains required resources, excludes test/platform/dependency classes, and contains `plugin.yml` version `1.0.0`.
- Production artifact: `target/RelicPrison-1.0.0.jar`.
- Production JAR entries: 557.
- Production JAR SHA-256: `38A95DDBC642AF7CA02D22DC439E41CADAF5F8C9F980C196C4769B776611D912`.

## Startup Presentation Coverage

`StartupReporterTest` adds 7 pure formatting/model tests covering:

- RelicPrison branding and dynamic version insertion.
- Explicit READY state.
- millisecond/second startup-duration formatting.
- ENABLED, DISABLED, NOT INSTALLED, and UNAVAILABLE integration states.
- disabled-banner behavior.
- absence of a hardcoded RC6 version.
- maximum formatted line width of 78 characters.

`ColorUtilTest` adds 2 tests preserving ampersand and stored legacy formatting while using Adventure components.

## Warning Verification

Production and test compilation run with `-Xlint:deprecation`, `-Xlint:removal`, and warning-as-error enforcement. The final clean verify log contains no compiler, Java native-access, SLF4J-provider, Maven runtime, or repository-controlled action deprecation warnings.

## Manual Boundary

Automated verification does not replace real Paper 1.21.10 startup, Java/Bedrock client rendering, optional integration behavior, restart/crash recovery, backup restore, database outage, or two-server database staging. Those items remain unchecked in `STAGING-CHECKLIST-1.0.0.md`.
