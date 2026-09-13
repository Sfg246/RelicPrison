# Phase 1 and Phase 2 corrective completion

## Release

`RelicPrison 0.6.1-mines`

This pass closes the main safety gaps left after the original `0.6.0-mines` implementation. It does not begin Phase 3 progression transactions or later roadmap systems.

## Durable mine copy and movement

Every physical copy or move receives a UUID and is written to `plugins/RelicPrison/structure-operations.yml` before world mutation begins.

Operation stages:

1. `PREPARED`
2. `TARGET_REGISTERED`
3. `COPYING`
4. `DESTINATION_COPIED`
5. `TARGET_ACTIVATED`
6. `CLEARING_SOURCE` for MOVE operations
7. `COMPLETED`

Recovery stages:

- `ROLLING_BACK`
- `ROLLED_BACK`
- `FAILED`

Only `COMPLETED` and `ROLLED_BACK` are terminal. Any other stage is loaded and locked after restart so staff can inspect and recover it.

### Commands

```text
/relicmine structure list
/relicmine structure info <operation-uuid>
/relicmine structure retry <operation-uuid>
/relicmine structure rollback <operation-uuid>
```

These commands use the existing `relicprison.admin.mine` permission.

### Provider selection

- FAWE is preferred when enabled and available.
- WorldEdit is used when available without FAWE.
- Sliced Bukkit copying is the fallback.
- ItemsAdder structures use the sliced Bukkit path so custom block identities can be copied and removed through ItemsAdder.
- Entities and biomes are not copied.

## Safety rules

- Source and destination dimensions must match.
- Source and destination cannot overlap.
- Destination cannot overlap another registered mine.
- Destination must be empty before registration or copying.
- Both worlds must be loaded.
- Players are evacuated before mutation.
- Resets and incompatible mine edits are blocked during an operation.
- Physical source blocks are not cleared until a MOVE target is active.
- A crash during copy is recovered by clearing the partial target and retrying, or by rolling back.
- A crash during source clearing can retry the clear or restore the source from the active target.

## Reconnect-safe profile loading

Loads are owned by a monotonically increasing player session ID. An unfinished load from an old session is allowed to finish, but its result cannot update the new session. The current session automatically starts a fresh load when the stale attempt leaves no valid cached profile.

## Reload behavior

`/rp reload all` validates and previews supported files before changing live state. Changes to database/storage configuration or the reset runtime save interval are rejected with a restart-required message.

If applying a supported reload fails, RelicPrison restores the prior configuration, messages, mines, progression definitions, GUIs, selling, boosters, mining configuration, Custom Drops, formatting, and player starting rank, then reinitializes affected runtime integrations and schedulers from that previous snapshot.

## Required staging checks

Run with Java 21 and Paper 1.21.10:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

Then test on a private staging server:

1. Join, disconnect during profile loading, and immediately reconnect.
2. Run a valid `/rp reload all`.
3. Run `/rp reload all` with one intentionally invalid file and confirm no live setting changes.
4. Copy a mine with FAWE or WorldEdit available.
5. Repeat with the Bukkit fallback.
6. Move a mine and verify its spawn offset.
7. Stop the server during destination copying, restart, inspect the operation, and retry.
8. Stop the server while a MOVE clears its source, restart, and test both retry and rollback on separate staging copies.
9. Attempt reset, delete, edit, block break/place, piston, fluid, and explosion actions in locked regions.
10. Test an ItemsAdder structure when ItemsAdder is installed.
11. Reload integrations repeatedly and confirm one AdvancedEnchantments bridge invocation per event.

Do not perform destructive interruption testing on production worlds.
