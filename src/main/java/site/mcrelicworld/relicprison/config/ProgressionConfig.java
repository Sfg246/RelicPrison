package site.mcrelicworld.relicprison.config;

import org.bukkit.Sound;

public record ProgressionConfig(
        String startingRank,
        String rankGroupFormat,
        String prestigeGroupFormat,
        boolean broadcastRankups,
        boolean broadcastPrestiges,
        int prestigeConfirmationSeconds,
        boolean repairOnJoin,
        boolean titleNotifications,
        boolean soundNotifications,
        Sound rankupSound,
        Sound prestigeSound
) {}
