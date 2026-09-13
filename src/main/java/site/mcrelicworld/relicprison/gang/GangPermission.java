package site.mcrelicworld.relicprison.gang;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum GangPermission {
    INVITE,
    KICK,
    PROMOTE,
    DEMOTE,
    MANAGE_RANKS,
    MANAGE_RANK_PERMISSIONS,
    DEPOSIT_BANK,
    WITHDRAW_BANK,
    PURCHASE_UPGRADES,
    MANAGE_GANG_BOOSTERS,
    EDIT_IDENTITY,
    EDIT_PRIVACY,
    SET_GANG_HOME,
    USE_GANG_HOME,
    MANAGE_GANG_CHAT,
    VIEW_AUDIT_LOG,
    TRANSFER_OWNERSHIP,
    DISBAND;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Set<GangPermission> ownerPermissions() {
        return Set.copyOf(EnumSet.allOf(GangPermission.class));
    }

    public static GangPermission parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
