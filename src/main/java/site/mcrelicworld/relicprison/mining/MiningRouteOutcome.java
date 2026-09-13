package site.mcrelicworld.relicprison.mining;

public record MiningRouteOutcome(long soldItems, long pickedItems, long droppedItems, boolean autoBlockApplied) {
    public MiningRouteOutcome {
        soldItems = Math.max(0L, soldItems);
        pickedItems = Math.max(0L, pickedItems);
        droppedItems = Math.max(0L, droppedItems);
    }

    public boolean autoSellBlock() {
        return soldItems > 0;
    }

    public boolean autoPickupBlock() {
        return soldItems == 0 && pickedItems > 0 && droppedItems == 0;
    }

    public PrimaryRoute primaryRoute() {
        if (soldItems > 0) return PrimaryRoute.AUTOSELL;
        if (pickedItems > 0 && droppedItems == 0) return PrimaryRoute.AUTOPICKUP;
        if (droppedItems > 0) return PrimaryRoute.DROPPED;
        return PrimaryRoute.NONE;
    }

    public enum PrimaryRoute { AUTOSELL, AUTOPICKUP, DROPPED, NONE }
}
