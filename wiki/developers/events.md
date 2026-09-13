# Developer Events

RelicPrison publishes Bukkit events for important mine, progression, booster, selling, mining, and player-data boundaries.

## Current event surface

### Mines

- `RelicMineCreateEvent`
- `RelicMineCreatedEvent`
- `RelicMineUpdateEvent`
- `RelicMineUpdatedEvent`
- `RelicMineDeleteEvent`
- `RelicMineDeletedEvent`
- `RelicMineResetPrepareEvent`
- `RelicMineResetStartEvent`
- `RelicMineResetCompleteEvent`
- `RelicMineResetFailEvent`
- `RelicMineBlockProcessEvent`

### Progression / data

- `RelicPlayerDataLoadEvent`
- `RelicPlayerRankUpEvent`
- `RelicPlayerPrestigeEvent`
- `RelicPlayerProgressionRepairEvent`

### Economy / mining

- `RelicSellEvent`
- `RelicAutoSellEvent`
- `RelicBoosterActivateEvent`
- `RelicBoosterExpireEvent`

## Listener example

```java
public final class RelicListener implements Listener {
    @EventHandler
    public void onRankUp(RelicPlayerRankUpEvent event) {
        getLogger().info("RelicPrison rankup event: " + event);
    }
}
```

Do not assume an event is cancellable because another event with a similar name is cancellable. Check the exact event class/signature.

## Before/after pairs

Several mine operations expose a before-style and after-style pair (`Create` / `Created`, `Update` / `Updated`, `Delete` / `Deleted`). Use the exact source contract to decide whether you are validating an operation or observing its committed result.

## Complete generated event signatures

The event inventory and extracted public signatures are generated during every documentation build:

**[Generated Event Reference](/generated/api/events)**

This prevents a newly added API event from being invisible in the published wiki.
