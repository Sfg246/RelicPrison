# RC6 Stage 6 Staging Status

Current recommended documented build:

```text
RelicPrison 1.0.0-rc6-stage6
Paper 1.21.10
Java 25
```

## Implemented areas

The current state includes, among other systems:

- normal/bulk mining pipeline and durable transaction/recovery records;
- crash-aware progression transaction states and refund handling;
- persistent reward packages/components and retry/recovery behavior;
- block-event claim/reward planning;
- leaderboard period finalization and reward state;
- admin GUI editors with stale-revision rejection and audit records;
- persistent boosters;
- player/mine statistics and leaderboard snapshots;
- backup verification, safety backups, staged restore, restore journal and rollback;
- native gangs with ranks, bank, upgrades, missions, boosters, chat, homes, leaderboards, seasons, placeholders, and audit records;
- consistent GUI/message/help surfaces;
- categorized/paginated command help.

## Manual staging still required

Do not treat a green Maven build as the same thing as production certification.

Current manual verification work includes real Paper/integration/client tests such as:

- Paper 1.21.10 startup/reload;
- AdvancedEnchantments 9.22.9 Drill/Trench/Seismic Drill, durability, fortune, outage behavior;
- ItemsAdder custom block/item behavior;
- Vault failure/ambiguous withdrawal reconciliation;
- LuckPerms update failure/repair;
- Java and Bedrock GUI/chat workflows;
- backup restore/rollback on staging;
- two real Paper servers sharing one supported database;
- real crash/partial-mutation/reconnect/offline-delivery scenarios.

## Known boundaries

- external Bukkit/third-party commands cannot be made perfectly exactly-once unless the receiver participates in idempotency/transactions;
- Bukkit inventory/entity persistence is not the same transaction as SQL;
- remote MySQL restore is not automated;
- multi-server and Bedrock behavior still need the real staging matrix;
- no generic foreign Factions schema is guessed for gang migration;
- this RC should remain a controlled staging build until the remaining matrix is passed.

## Release gate mindset

A production promotion should require:

1. clean Maven verification;
2. clean fresh staging startup;
3. clean upgrade staging startup;
4. economy/LuckPerms failure tests;
5. mine reset and mining integration tests;
6. backup verify/restore drill;
7. Java and Bedrock UX pass;
8. multi-server concurrency pass if the network uses shared storage;
9. no unresolved high-severity diagnostic/audit/recovery findings.
