# Error Encyclopedia

Search this page by the exact phrase you saw. The fix order is intentionally conservative so you do not destroy good data while troubleshooting.

## “Java version … required [25,26)” / wrong Java runtime

**Meaning:** the current build requires Java 25.

**Fix:** make the server process and build runner resolve Java 25, then confirm with `java -version`. Do not weaken the project’s Java requirement to hide a bad host/runtime configuration.

## RelicPrison cannot start because Vault/economy is unavailable

**Meaning:** Vault is a hard dependency and RelicPrison also needs a registered economy provider.

**Fix:** verify Vault loads, verify the economy plugin loads, then verify Vault sees that provider. Do not delete player data.

## LuckPerms unavailable / progression permission repair fails

**Meaning:** RelicPrison could not perform the required permission/group operation.

**Fix:** check LuckPerms health first, then use RelicPrison diagnostics/repair tools after the underlying permission service is healthy.

```text
/rp diagnose
/rp repair <player>
```

## “selection incomplete”

**Meaning:** a mine create/resize/move action was requested without a complete two-point selection.

**Fix:** use `/relicmine wand`, select both opposite corners, then rerun the operation.

## “Mine world is not loaded”

**Meaning:** the mine definition references a world Bukkit cannot currently resolve.

**Fix:** confirm the configured world name/UUID, confirm the world exists and is loaded, and do not blindly rewrite coordinates before understanding why the world is missing.

## “Mine already exists”

**Meaning:** the normalized mine ID is already registered.

**Fix:** choose a different ID or intentionally rename/edit the existing mine. Mine IDs are not disposable display labels.

## “Mine not found”

**Meaning:** the normalized ID does not match a loaded mine.

**Fix:** run `/relicmine list`, copy the exact ID, then retry.

## “You do not have a pending mine action”

**Meaning:** `/relicmine confirm` or `/relicmine cancel` was used after the preview session disappeared, expired, or was never created.

**Fix:** start the protected create/move/copy/delete operation again and confirm within the active session.

## Selection is too large / overlaps another mine

**Meaning:** the selection violates `selection.maximum-volume` or overlap policy.

**Fix:** shrink/move the region or deliberately adjust the policy only after checking why the safety limit exists.

## Invalid/unknown block type

**Meaning:** the composition entry is neither a valid vanilla material nor a recognized namespaced custom block form.

**Fix:** test a vanilla material first. For ItemsAdder, verify the custom ID exists and use a clear ID such as:

```yaml
block: itemsadder:namespace:block_id
```

## Composition behaves incorrectly

**Meaning:** weights or providers do not match your intended distribution.

**Fix:** inspect the composition and normalize after changes:

```text
/relicmine composition <mine> list
/relicmine composition <mine> normalize
```

Then force one reset and inspect actual blocks.

## Reset stays queued / does not run

**Check in order:** active reset state → concurrency limit → MSPT pause threshold → world availability → failed reset record → custom block provider.

```text
/relicmine info <mine>
/relicmine recount <mine>
/rp diagnose
```

If a recoverable failure is recorded:

```text
/relicmine retryfailed <mine>
```

## Reset causes lag

**Do not** immediately raise `max-blocks-per-tick`. The reset engine intentionally uses timing targets, min/max throughput, concurrency, and MSPT pause/resume behavior. Tune from measurements.

## Database connection failure

**SQLite:** check file permissions, disk space, path, WAL/checkpoint errors, and whether another process is interfering.

**MySQL:** check host/port/database/user/password, network reachability, server timezone/SSL parameters, connection limits, and database permissions.

Do not switch database type as an emergency “fix” unless you have an actual migration plan.

## Player profile is `loading` / placeholder remains `loading`

**Meaning:** cached/profile/leaderboard data may not be ready, or a backing service is unhealthy.

**Fix:** wait for a normal load, then inspect `/rp diagnose`. If the value never resolves, inspect database and PlaceholderAPI health.

## Placeholder returns `invalid`

**Meaning:** the dynamic placeholder is malformed or references an unknown target.

Common examples: invalid material, unknown mine ID, unknown leaderboard board, or invalid top-position suffix.

## ItemsAdder custom block fails

**Fix order:** vanilla mine works → ItemsAdder itself healthy → integration enabled → ID exists → one custom placement works → one custom break/drop works → selling tested separately.

## AdvancedEnchantments bulk mining is wrong

**Capture:** enchant name/level, tool, block layout, expected affected blocks, actual affected blocks, fortune result, durability result, console output, and whether the same layout works with vanilla mining.

Current RC status still requires real-server verification of important AE mining behaviors.

## Gang bank balance disagreement

Vault and the gang SQL transaction cannot be one physical transaction. RelicPrison has compensation/reconciliation paths, but a hard crash at the wrong boundary can require operator inspection.

Use:

```text
/gang bank history
/relicgang inspect <gang>
/relicgang audit <gang>
/rp diagnose
```

## Backup verification fails

Do not restore it. Verification intentionally rejects checksum mismatch, corrupt ZIPs, unsafe paths, duplicate entries, unreadable required YAML, and unsupported/invalid snapshots.

Create a fresh backup after investigating the failure.

## MySQL restore is refused

Automatic remote MySQL data restoration is intentionally not implemented. Config-only restore is different. For MySQL data, stop writes and use the logical SQL export with an explicit manual database restore plan.

## YAML changed but behavior did not

Check: wrong server folder, unsupported hot reload, invalid config rejected, feature gate disabled, startup-only setting, stale copy of the file, or another prerequisite still blocking the behavior.

Always finish with:

```text
/rp validate
/rp diagnose
```
