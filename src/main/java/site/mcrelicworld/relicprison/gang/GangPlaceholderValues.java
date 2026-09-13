package site.mcrelicworld.relicprison.gang;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

public final class GangPlaceholderValues {
    private GangPlaceholderValues() { }

    public static String resolve(String key, Gang gang, GangRank rank, GangStatistics statistics,
                                 GangMemberStatistics contribution, int onlineMembers,
                                 Map<String, Integer> positions, Function<BigDecimal, String> currency,
                                 String unavailable) {
        if (gang == null) return unavailable;
        return switch (key) {
            case "gang_name" -> gang.name();
            case "gang_tag" -> gang.tag();
            case "gang_rank" -> rank == null ? unavailable : rank.displayName();
            case "gang_level" -> String.valueOf(gang.level());
            case "gang_xp" -> gang.xp().stripTrailingZeros().toPlainString();
            case "gang_points" -> String.valueOf(gang.points());
            case "gang_member_count" -> String.valueOf(gang.memberCount());
            case "gang_online_members" -> String.valueOf(onlineMembers);
            case "gang_balance" -> currency.apply(gang.bankBalance());
            case "gang_leaderboard_position" -> position(positions, "level");
            case "gang_contribution_blocks" -> contribution == null ? "0" : String.valueOf(contribution.blocks());
            case "gang_contribution_money" -> contribution == null ? "0" : contribution.money().toPlainString();
            case "gang_contribution_xp" -> contribution == null ? "0" : contribution.gangXp().toPlainString();
            case "gang_contribution_rankups" -> contribution == null ? "0" : String.valueOf(contribution.rankups());
            case "gang_contribution_prestiges" -> contribution == null ? "0" : String.valueOf(contribution.prestiges());
            case "gang_contribution_block_events" -> contribution == null ? "0" : String.valueOf(contribution.blockEvents());
            case "gang_blocks" -> statistics == null ? "0" : String.valueOf(statistics.blocks());
            case "gang_money_earned" -> statistics == null ? "0" : statistics.money().toPlainString();
            case "gang_prestiges" -> statistics == null ? "0" : String.valueOf(statistics.prestiges());
            case "gang_block_events" -> statistics == null ? "0" : String.valueOf(statistics.blockEvents());
            default -> key.startsWith("gang_leaderboard_position_")
                    ? position(positions, key.substring("gang_leaderboard_position_".length())) : null;
        };
    }

    private static String position(Map<String, Integer> positions, String category) {
        Integer position = positions.get(category);
        return position == null ? "unranked" : String.valueOf(position);
    }
}
