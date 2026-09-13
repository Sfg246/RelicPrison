package site.mcrelicworld.relicprison.gang;

import java.util.UUID;

public record GangInvite(
        UUID id,
        UUID gangId,
        UUID playerId,
        UUID invitedBy,
        long createdAt,
        long expiresAt,
        Status status
) {
    public enum Status { PENDING, ACCEPTED, DENIED, EXPIRED, REVOKED }

    public boolean active(long now) {
        return status == Status.PENDING && expiresAt > now;
    }
}
