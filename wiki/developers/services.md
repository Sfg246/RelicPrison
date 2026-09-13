# Developer Services

RelicPrison exposes its public API through Bukkit's service manager. The root `RelicPrisonApi` is a stable doorway to narrower services instead of encouraging other plugins to reach into implementation classes.

## Getting the API

```java
RelicPrisonApi api = Bukkit.getServicesManager().load(RelicPrisonApi.class);
if (api == null) {
    // RelicPrison is not ready/available.
    return;
}

getLogger().info("RelicPrison " + api.version());
```

Add `RelicPrison` as a plugin dependency/soft dependency according to whether your plugin can run without it, and never assume the service exists before RelicPrison has enabled.

## Service map

| Service | Purpose | Threading headline |
| --- | --- | --- |
| `MineService` | Mine catalog and spatial lookups | Thread-safe immutable reads |
| `MineResetService` | Reset state and reset requests | Reads are safe; mutating reset requests are main-thread only |
| `MineAccessService` | Cached mine-access decisions | Thread-safe cached reads |
| `RankService` | Immutable rank catalog | Thread-safe reads |
| `PrestigeService` | Immutable prestige catalog | Thread-safe reads |
| `ProgressionService` | Rankup, prestige, repair | Starts on main thread; repair returns a future |
| `PlayerDataService` | Player profile views/loads | Futures; do not `join()` them on the main thread |
| `SellService` | Selling/value operations | Inventory mutation is main-thread only; futures must not be joined on main |
| `MultiplierService` | Current multiplier values | Thread-safe cached reads |
| `BoosterService` | Booster views/activation | Reads safe; activation is asynchronous/future-backed |
| `MiningService` | Mining pipeline counters/status | Thread-safe counters/read surface |
| `StatisticsService` | Stats and leaderboards | Cached reads; leaderboard futures must not block main thread |
| `BackupService` | Backup operations | Asynchronous file/database work |
| `DiagnosticService` | Diagnostic snapshots/exports | Asynchronous exports; immutable snapshots |
| `NumberFormatService` | RelicPrison-consistent number formatting | Thread-safe immutable formatting snapshot |

## Example: mine lookup

```java
RelicPrisonApi api = Bukkit.getServicesManager().load(RelicPrisonApi.class);
if (api == null) return;

api.mines().findMine("a").ifPresent(mine -> {
    getLogger().info("Mine A: " + mine);
});
```

`MineService` also exposes immutable mine collections and spatial lookups by world name/UUID plus block coordinates.

## Do not couple to implementation packages

Avoid imports from packages such as `internal`, repositories, command handlers, or concrete `*Impl` classes. Those are implementation details and may change more aggressively than `site.mcrelicworld.relicprison.api`.

## Complete generated signatures

The exact interface method declarations are regenerated from the Java source on every docs build:

**[Generated API Service Reference](/generated/api/services)**

That page is the quickest way to verify the current method name/signature without trusting an old wiki copy.
