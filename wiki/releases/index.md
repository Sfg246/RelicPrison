# Release & Download Status

<div class="release-hero">
<strong>Current documented build:</strong> <code>RelicPrison 1.0.0-rc6-stage6</code><br><br>
<span class="status-pill">Paper 1.21.10</span>
<span class="status-pill">Java 25</span>
<span class="status-pill">Maven</span>
<span class="status-pill">Controlled staging</span>
</div>

## Is this a production release?

**No.** The current source explicitly recommends RC6 Stage 6 for controlled Paper staging only until the real integration/client/failure matrix is completed.

## Where is the JAR?

The Maven workflow builds the production JAR, calculates `SHA256.txt`, and uploads CI artifacts for successful runs.

- [GitHub Actions](https://github.com/Sfg246/RelicPrison/actions)
- [Repository](https://github.com/Sfg246/RelicPrison)
- [GitHub Releases](https://github.com/Sfg246/RelicPrison/releases)

::: info No formal GitHub Release yet
There is currently no published entry in GitHub Releases. This page intentionally does not invent a “latest release” download button. When `1.0.0` is actually published, this page should point to that immutable release and checksum.
:::

## What to verify before using an artifact

1. Workflow conclusion is successful.
2. Artifact name/version matches the commit you intended to test.
3. SHA-256 file belongs to the same workflow run.
4. Server is Paper 1.21.10 on Java 25.
5. Required dependencies and economy provider are present.
6. You have a backup and are using staging access first.

## Current release gate

See [RC6 Staging Status](/releases/rc6) and the [Compatibility Matrix](/reference/compatibility). The remaining gate is dominated by real Paper, optional integration, Java/Bedrock client, restore, failure, and two-server tests rather than compilation alone.
