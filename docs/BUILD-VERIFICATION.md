# Build Verification

This file supersedes older Stage 14 build notes. The authoritative release-candidate evidence for this pass is:

- `BUILD-REPORT-1.0.0-rc6-stage6.md`
- `TEST-REPORT-1.0.0-rc6-stage6.md`
- `MIGRATION-REPORT-1.0.0-rc6-stage6.md`
- `STAGING-CHECKLIST-1.0.0-rc6-stage6.md`
- `KNOWN-LIMITATIONS-1.0.0-rc6-stage6.md`
- `ADMIN-GUI-FIELD-MAPPING-1.0.0-rc6-stage4.md`
- `SHA256SUMS-1.0.0-rc6-stage6.txt`

## Current Verified Build

- Project version: `1.0.0-rc6-stage6`
- Java: Temurin `25.0.4.1`
- Maven Wrapper: Apache Maven `3.9.11`
- Paper API: `1.21.10-R0.1-SNAPSHOT`
- Commands: `.\mvnw.cmd clean test`; `.\mvnw.cmd clean package`
- Automated result in this pass: PASS
- Test total in this pass: 156 tests, 0 failures, 0 errors, 0 skipped
- Artifact: `target/RelicPrison-1.0.0-rc6-stage6.jar`
- SHA-256 values are recorded in `SHA256SUMS-1.0.0-rc6-stage6.txt` after the final clean package.

## Release Boundary

This repository is not production-ready solely because it compiles. Production release requires the automated checks plus real Paper 1.21.10 staging with the supported integration versions and Java/Bedrock clients. RC6 Stage 4 is the controlled-staging GUI editor candidate.


