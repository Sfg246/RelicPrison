package site.mcrelicworld.relicprison.config;

import java.util.List;

public record TeleportConfig(
        int warmupSeconds,
        boolean cancelOnMove,
        boolean cancelOnDamage,
        boolean enforceMineEntry,
        boolean allowPreviousMines,
        boolean teleportFirstJoinToMine,
        List<String> firstJoinCommands
) {
    public TeleportConfig { firstJoinCommands = List.copyOf(firstJoinCommands); }
}
