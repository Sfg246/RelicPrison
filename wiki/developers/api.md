# Developer API & Thread Safety

RelicPrison exposes a Bukkit service API instead of requiring other plugins to reach into implementation classes.

## Obtain the API

The plugin registers `RelicPrisonApi` in Bukkit's `ServicesManager` at `ServicePriority.Normal`.

Example:

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import site.mcrelicworld.relicprison.api.RelicPrisonApi;

RegisteredServiceProvider<RelicPrisonApi> registration =
        Bukkit.getServicesManager().getRegistration(RelicPrisonApi.class);

if (registration == null) {
    throw new IllegalStateException("RelicPrison API is not available yet");
}

RelicPrisonApi api = registration.getProvider();
```

Declare RelicPrison as an appropriate dependency/soft dependency for your integration and do not request the service before RelicPrison has registered it.

## Top-level services

`RelicPrisonApi` currently exposes:

```java
MineService mines();
MineResetService resets();
MineAccessService mineAccess();
RankService ranks();
PrestigeService prestiges();
ProgressionService progression();
PlayerDataService players();
SellService selling();
MultiplierService multipliers();
BoosterService boosters();
MiningService mining();
StatisticsService statistics();
BackupService backups();
DiagnosticService diagnostics();
NumberFormatService numbers();
String version();
```

## The most important API rule

**Do not block the Paper main thread waiting for futures.**

Several API methods deliberately return asynchronous work.

Bad pattern:

```java
// Do not do this on the server thread.
var profile = api.players().load(playerId).join();
```

Better pattern:

```java
api.players().load(playerId).thenAccept(profile -> {
    // Continue asynchronously, and schedule Bukkit-only work
    // back to the server thread when required.
});
```

## Thread-safety summary

### Main-thread-only or Bukkit-sensitive work

Treat these as server-thread work unless the specific API contract says otherwise:

- mining block mutation;
- bulk block mutation;
- Bukkit inventory mutation;
- Vault calls touching Bukkit/plugin economy state;
- console command dispatch;
- GUI operations;
- mine reset block placement;
- ItemsAdder custom block placement/removal;
- rankup/prestige starts.

### Safe/read-oriented services

The API interface documents thread-safe read services such as mine/rank/prestige catalog reads, cached mine-access decisions, multiplier reads, number formatting, and version retrieval.

### Asynchronous services

Database/profile load-save work, backups, exports, leaderboard queries, and other persistence operations use async/future-style behavior.

## Service-specific notes

### `mines()`

Thread-safe catalog access. Use this to inspect configured mines rather than parsing `mines.yml` yourself.

### `resets()`

Read methods are thread-safe; mutating reset requests are documented as main-thread-only.

### `mineAccess()`

Thread-safe cached access decisions for loaded profiles.

### `ranks()` / `prestiges()`

Immutable catalog-style reads.

### `progression()`

Rankup/prestige starts are main-thread-sensitive. Repair returns asynchronous work.

### `players()`

Profile loads return futures. Never `.join()` them on the Paper thread.

### `selling()`

Inventory operations are main-thread-only; returned futures must not be joined on the main thread.

### `boosters()`

Cached reads are safe; activation futures should not be joined on the main thread.

### `statistics()`

Cached counters are safe to read; leaderboard futures should not be joined on the main thread.

### `backups()` / `diagnostics()`

These are asynchronous IO-oriented services.

## Events

The public API event package includes RelicPrison-specific Bukkit events for major lifecycle/gameplay surfaces. Current examples include:

```text
RelicAutoSellEvent
RelicBoosterActivateEvent
RelicBoosterExpireEvent
RelicMineBlockProcessEvent
RelicMineCreateEvent
RelicMineCreatedEvent
RelicMineDeleteEvent
RelicMineDeletedEvent
RelicMineResetPrepareEvent
RelicMineResetCompleteEvent
RelicMineResetFailEvent
```

Use Bukkit's normal event listener model. Check the event class you target for mutability/cancellation semantics instead of assuming every “prepare” and “complete” event behaves the same.

## Why the API exists

Avoid this:

```java
RelicPrisonPlugin plugin = ...;
plugin.someImplementationField...
```

Prefer this:

```java
RelicPrisonApi api = ...;
api.mines()...
```

The API boundary makes integrations less dependent on internal package layout and lets RelicPrison enforce its thread-safety/recovery rules.

## Persistence/idempotency note

RelicPrison has durable ledgers for several high-value operations, but a third-party command or external plugin operation is not automatically part of the same SQL transaction.

If your integration performs a non-idempotent external side effect, design your own duplicate protection and failure recovery instead of assuming “the event only fires once” is enough.
