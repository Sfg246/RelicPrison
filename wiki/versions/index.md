# Versioned Documentation

RelicPrison’s docs are version-aware so a future stable release does not silently rewrite instructions for an older server.

## Current documentation channel

<span class="status-pill">1.0.0-rc6-stage6</span>
<span class="status-pill">Paper 1.21.10</span>
<span class="status-pill">Java 25</span>

The live root documentation currently describes RC6 Stage 6.

## Version policy

When a stable or incompatible release is published:

1. Freeze a documentation snapshot for the outgoing version.
2. Preserve its command/config/API reference.
3. Move the root docs to the new recommended version.
4. Add an explicit migration page describing changed keys, database migrations, commands, API changes, and breaking behavior.
5. Keep old version pages available instead of silently rewriting history.

## Available channels

| Channel | Status | Documentation |
| --- | --- | --- |
| `1.0.0-rc6-stage6` | Current RC staging documentation | [RC6 status](/releases/rc6) |
| `1.0.0` | Not released | No version is fabricated before a real release exists. |

## Why there is no fake 1.0 documentation

GitHub currently has no formal RelicPrison Release published. The repository and CI can build the RC, but a successful build is not the same thing as a signed-off production release. This wiki keeps that distinction visible.
