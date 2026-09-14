# Release & Download Status

<div class="release-hero">
<strong>Current release:</strong> <code>RelicPrison 1.0.0</code><br><br>
<span class="status-pill">Paper 1.21.10</span>
<span class="status-pill">Java 25</span>
<span class="status-pill">Stable / final</span>
</div>

## Release status

`1.0.0` is the official stable version identifier and is not a prerelease. Automated verification and exact artifact checks are recorded in the root `TEST-REPORT-1.0.0.md` and `BUILD-REPORT-1.0.0.md` files.

Real Paper, optional-integration, Java/Bedrock client, restore, failure-injection, and two-server verification remains explicitly pending. The final version label does not manufacture production certification for an operator's specific environment.

## Where is the JAR?

The Maven workflow builds `RelicPrison-1.0.0.jar`, calculates `SHA256.txt`, and uploads both for successful runs.

- [GitHub Actions](https://github.com/Sfg246/RelicPrison/actions)
- [Repository](https://github.com/Sfg246/RelicPrison)
- [GitHub Releases](https://github.com/Sfg246/RelicPrison/releases)

Use the immutable `v1.0.0` GitHub Release when available and verify its checksum before installation.

## What to verify before using an artifact

1. Workflow conclusion is successful.
2. Artifact version is exactly `1.0.0`.
3. `SHA256.txt` belongs to the same workflow or release.
4. Server is Paper 1.21.10 on Java 25.
5. Required dependencies and an economy provider are present.
6. The documented manual matrix is evaluated for the intended deployment.

See [1.0.0 Release Notes](/releases/1.0.0), [RC6 History](/releases/rc6), and [Known Limitations](/known-limitations).
