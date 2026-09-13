# LuckPerms 5.4.164 Verification

RelicPrison `0.2.4-stage10-lp54164` was compiled and bytecode-checked against the exact uploaded runtime:

- File: `LuckPerms-Bukkit-5.4.164.jar`
- Runtime plugin version: `5.4.164`
- Public API line: `5.4`

## Integration design

RelicPrison stores rank and prestige progression in its own player database. LuckPerms mirrors the saved state:

- One direct rank group per player
- One direct prestige group per player
- `rank-b` inherits `rank-a`, continuing through `rank-z`
- Prestiging removes the direct Rank Z group and assigns Rank A
- Removing Rank Z also removes permissions inherited through its rank chain
- Repair-on-join restores LuckPerms from RelicPrison when the two become inconsistent

## Binary-linkage hardening

Earlier offline builds linked calls to covariant interface owners that were not declared directly by the runtime interface. This caused `NoSuchMethodError` despite the source appearing compatible.

The 5.4.164 build deliberately links mutation calls to the interfaces that actually declare them in the uploaded JAR:

- `PermissionHolder.data()`
- `PermissionHolder.getNodes(NodeType)`
- `NodeBuilder.build()`
- `NodeMap.add(Node)` and `NodeMap.remove(Node)`
- `GroupManager.saveGroup(Group)`
- `UserManager.saveUser(User)`

Bytecode inspection confirmed there are no calls to nonexistent `Group.data()`, `User.data()`, or `InheritanceNode.Builder.build()` method descriptors.

## Upgrade rule

A Minecraft/Paper update does not automatically require a LuckPerms integration rewrite. When LuckPerms itself is updated, test rankup, rankupmax, prestige, and repair-on-join on staging. RelicPrison logs a warning when the detected LuckPerms runtime differs from the verified `5.4.164` build.
