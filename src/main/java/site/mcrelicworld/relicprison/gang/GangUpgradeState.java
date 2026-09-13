package site.mcrelicworld.relicprison.gang;

import java.util.UUID;

public record GangUpgradeState(UUID gangId, String upgradeId, int tier, long updatedAt, UUID updatedBy) { }
