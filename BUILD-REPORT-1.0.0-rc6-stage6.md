# Build Report - 1.0.0-rc6-stage6

- Date: 2026-09-12
- Java: Eclipse Temurin 25.0.4.1
- Maven Wrapper: Apache Maven 3.9.11
- Paper API: 1.21.10-R0.1-SNAPSHOT
- Required test command: `.\mvnw.cmd clean test` - PASS
- Required package command: `.\mvnw.cmd clean package` - PASS
- Additional verification: `.\mvnw.cmd -B -ntp verify` - PASS, including Checkstyle and JAR content integration verification
- Automated unit/behavior total: 174 tests, 0 failures, 0 errors, 0 skipped
- Build integration total: 1 test, 0 failures, 0 errors, 0 skipped
- Checkstyle: 0 violations
- Production artifact: `target/RelicPrison-1.0.0-rc6-stage6.jar`
- Sources JAR: `target/RelicPrison-1.0.0-rc6-stage6-sources.jar`
- Source archive: `RelicPrison-1.0.0-rc6-stage6-source.zip`
- Full report archive: `RelicPrison-1.0.0-rc6-stage6-test-reports.zip`
- Production JAR SHA-256: `1654C406B9EA42BA6B03B23C7C540231DE2748BEF7D78AC28A37E7559035F473`
- JAR audit: 548 entries; JAR content integration verification passed with no test classes or bundled platform/dependency classes.

SHA-256 values are recorded in `SHA256SUMS-1.0.0-rc6-stage6.txt`. This is a controlled-staging RC; no real Paper or client/integration staging was performed in this coding pass.
