package site.mcrelicworld.relicprison.access;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.MineAccessService;
import site.mcrelicworld.relicprison.database.PlayerProfile;
import site.mcrelicworld.relicprison.database.PlayerProfileRepository;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.MineServiceImpl;
import site.mcrelicworld.relicprison.progression.PrestigeServiceImpl;
import site.mcrelicworld.relicprison.progression.PrestigeDefinition;
import site.mcrelicworld.relicprison.progression.RankDefinition;
import site.mcrelicworld.relicprison.progression.RankServiceImpl;

import java.util.Optional;
import java.util.UUID;

public final class MineAccessServiceImpl implements MineAccessService {
    private final RelicPrisonPlugin plugin;
    private final MineServiceImpl mines;
    private final PlayerProfileRepository profiles;
    private final RankServiceImpl ranks;
    private final PrestigeServiceImpl prestiges;

    public MineAccessServiceImpl(RelicPrisonPlugin plugin, MineServiceImpl mines, PlayerProfileRepository profiles,
                                 RankServiceImpl ranks, PrestigeServiceImpl prestiges) {
        this.plugin = plugin;
        this.mines = mines;
        this.profiles = profiles;
        this.ranks = ranks;
        this.prestiges = prestiges;
    }

    @Override public boolean canEnter(UUID playerId, String mineId) { return canAccess(playerId, mineId); }
    @Override public boolean canMine(UUID playerId, String mineId) { return canAccess(playerId, mineId); }

    public boolean canAccess(UUID playerId, String mineId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.hasPermission("relicprison.bypass.mine-access")) return true;
        MineDefinition mine = mines.findMine(mineId).orElse(null);
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (mine == null || !mine.enabled()) return false;
        if (plugin.config().snapshot().isExcludedWorld(mine.worldName())) return true;
        if (profile == null) return false;
        if (mine.accessPermission() != null && (player == null || !player.hasPermission(mine.accessPermission()))) return false;
        if (mine.requiredRank() != null) {
            int playerOrder = ranks.indexOf(profile.currentRank());
            int requiredOrder = ranks.indexOf(mine.requiredRank());
            if (playerOrder < 0 || requiredOrder < 0) return false;
            if (plugin.config().snapshot().teleport().allowPreviousMines()) {
                if (playerOrder < requiredOrder) return false;
            } else if (playerOrder != requiredOrder) return false;
        }
        if (mine.requiredPrestige() != null) {
            int playerOrder = prestiges.indexOf(profile.currentPrestige());
            int requiredOrder = prestiges.indexOf(mine.requiredPrestige());
            if (playerOrder < requiredOrder || requiredOrder < 0) return false;
        }
        return true;
    }

    public Optional<MineDefinition> currentRankMine(UUID playerId) {
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (profile == null) return Optional.empty();
        RankDefinition rank = ranks.definition(profile.currentRank()).orElse(null);
        if (rank != null && rank.mineId() != null) return mines.findMine(rank.mineId());
        PrestigeDefinition prestige = prestiges.definition(profile.currentPrestige()).orElse(null);
        return prestige == null || prestige.mineId() == null ? Optional.empty() : mines.findMine(prestige.mineId());
    }

    public Optional<MineDefinition> bestAccessibleMine(UUID playerId) {
        PlayerProfile profile = profiles.cachedProfile(playerId).orElse(null);
        if (profile == null) return Optional.empty();
        return mines.mines().stream().filter(mine -> canAccess(playerId, mine.id()))
                .filter(mine -> mine.requiredRank() != null)
                .max(java.util.Comparator.comparingInt(mine -> ranks.indexOf(mine.requiredRank())));
    }
}
