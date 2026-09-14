# Build Verification

This file supersedes older Stage 14 build notes. The authoritative official-release evidence for this pass is:

- `BUILD-REPORT-1.0.0.md`
- `TEST-REPORT-1.0.0.md`
- `MIGRATION-REPORT-1.0.0.md`
- `STAGING-CHECKLIST-1.0.0.md`
- `KNOWN-LIMITATIONS-1.0.0.md`
- `ADMIN-GUI-FIELD-MAPPING-1.0.0-rc6-stage4.md`
- `SHA256.txt` in the release artifact bundle

## Current Verified Build

- Project version: `1.0.0`
- Java: Temurin `25.0.4.1`
- Maven Wrapper: Apache Maven `3.9.16`
- Paper API: `1.21.10-R0.1-SNAPSHOT`
- Command: `.\mvnw.cmd -B -ntp clean verify`
- Automated result in this pass: PASS
- Exact Surefire/Failsafe totals are recorded in `TEST-REPORT-1.0.0.md`.
- Artifact: `target/RelicPrison-1.0.0.jar`
- The release bundle records the exact JAR digest in `SHA256.txt`.

## Release Boundary

The official version number does not fabricate production certification. Real Paper 1.21.10 staging with supported integrations, Java/Bedrock clients, recovery, restore, and multi-server behavior remains pending as documented.


