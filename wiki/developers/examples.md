# Developer Examples

These examples show safe integration patterns. They deliberately avoid blocking database-backed futures on Paper's main thread.

## Obtain the API safely

```java
import org.bukkit.Bukkit;
import site.mcrelicworld.relicprison.api.RelicPrisonApi;

RelicPrisonApi relic = Bukkit.getServicesManager().load(RelicPrisonApi.class);
if (relic == null) {
    getLogger().warning("RelicPrison API is unavailable");
    return;
}
```

## Read a mine

```java
relic.mines().findMine("a").ifPresent(mine -> {
    getLogger().info("Found mine: " + mine);
});
```

Mine catalog reads are designed around immutable views/snapshots.

## Format a number like RelicPrison

```java
String version = relic.version();
var formatter = relic.numbers();
```

Use the generated service reference for the exact current formatting methods rather than duplicating RelicPrison abbreviation/currency rules.

## Work with asynchronous operations

Bad on the Paper main thread:

```java
// Do not do this with a database-backed future on the main thread.
var result = future.join();
```

Preferred pattern:

```java
future.whenComplete((result, error) -> {
    if (error != null) {
        getLogger().warning(error.getMessage());
        return;
    }
    // If you need to touch Bukkit world/inventory state here,
    // schedule that part back onto the server thread.
});
```

## Listen for an event

```java
public final class RelicHooks implements Listener {
    @EventHandler
    public void onSell(RelicSellEvent event) {
        // Observe/handle according to the exact event contract.
    }
}
```

## Maven coordinates versus a published repository

The project coordinates are:

```xml
<groupId>site.mcrelicworld</groupId>
<artifactId>relicprison</artifactId>
<version>1.0.0</version>
```

Those coordinates identify the project but **do not imply the artifact is published to Maven Central or another public Maven repository**. Until a repository/release publication is intentionally created, compile against an approved local/internal artifact rather than inventing a repository URL.

## Thread-safety rule of thumb

If it touches Bukkit world blocks, inventories, Vault calls, command dispatch, GUI state, or custom-block placement/removal, assume main-thread constraints unless the API explicitly says otherwise. Database, backup, profile loading, and many statistics/leaderboard operations are future/snapshot based.

See [API & Thread Safety](/developers/api) and the [Generated Service Reference](/generated/api/services).
