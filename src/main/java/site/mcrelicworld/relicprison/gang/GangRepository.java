package site.mcrelicworld.relicprison.gang;

import site.mcrelicworld.relicprison.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GangRepository {
    private final DatabaseManager database;

    public GangRepository(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Gang> create(UUID ownerId, String name, String tag, int memberLimit,
                                          List<GangConfig.DefaultRank> defaultRanks, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            if (memberByPlayer(active, ownerId).isPresent()) throw new Conflict("Player already belongs to a gang");
            UUID gangId = UUID.randomUUID();
            try (PreparedStatement statement = active.prepareStatement("INSERT INTO rp_gangs("
                    + "gang_id,name,normalized_name,tag,normalized_tag,description,owner_uuid,created_at,level,xp,points,"
                    + "bank_balance,member_limit,member_count,join_mode,color,motd,enabled,version,updated_at) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, gangId.toString());
                statement.setString(2, name);
                statement.setString(3, normalize(name));
                statement.setString(4, tag);
                statement.setString(5, normalize(tag));
                statement.setString(6, "");
                statement.setString(7, ownerId.toString());
                statement.setLong(8, now);
                statement.setInt(9, 1);
                statement.setBigDecimal(10, BigDecimal.ZERO);
                statement.setLong(11, 0L);
                statement.setBigDecimal(12, BigDecimal.ZERO);
                statement.setInt(13, memberLimit);
                statement.setInt(14, 1);
                statement.setString(15, GangJoinMode.INVITE_ONLY.name());
                statement.setString(16, "&7");
                statement.setString(17, "");
                statement.setBoolean(18, true);
                statement.setLong(19, 1L);
                statement.setLong(20, now);
                statement.executeUpdate();
            } catch (SQLException ex) {
                if (constraint(ex)) throw new Conflict("Gang name or tag is already in use", ex);
                throw ex;
            }
            UUID ownerRank = null;
            for (GangConfig.DefaultRank definition : defaultRanks) {
                UUID rankId = UUID.randomUUID();
                insertRank(active, new GangRank(rankId, gangId, definition.key(), definition.displayName(),
                        definition.priority(), definition.color(), true,
                        definition.key().equals("owner") ? GangPermission.ownerPermissions() : definition.permissions(),
                        now, now));
                if (definition.key().equals("owner")) ownerRank = rankId;
            }
            if (ownerRank == null) throw new Conflict("Default ranks do not contain Owner");
            insertMember(active, new GangMember(ownerId, gangId, ownerRank, now, now, false, 1L));
            try (PreparedStatement statement = active.prepareStatement("INSERT INTO rp_gang_statistics("
                    + "gang_id,blocks_mined,money_earned,gang_xp_earned,rankups,prestiges,block_events,updated_at) "
                    + "VALUES(?,?,?,?,?,?,?,?)")) {
                statement.setString(1, gangId.toString());
                statement.setLong(2, 0L);
                statement.setBigDecimal(3, BigDecimal.ZERO);
                statement.setBigDecimal(4, BigDecimal.ZERO);
                statement.setLong(5, 0L);
                statement.setLong(6, 0L);
                statement.setLong(7, 0L);
                statement.setLong(8, now);
                statement.executeUpdate();
            }
            audit(active, gangId, ownerId, "create", "gang", gangId.toString(), "", name, true, "", "", now);
            return gangById(active, gangId).orElseThrow();
        }));
    }

    public CompletableFuture<Optional<Gang>> findById(UUID gangId) {
        return database.submitIdempotent(connection -> gangById(connection, gangId));
    }

    public CompletableFuture<Optional<Gang>> findByNameOrTag(String value) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gangs WHERE "
                    + "(normalized_name=? OR normalized_tag=?) AND enabled=TRUE")) {
                statement.setString(1, normalize(value));
                statement.setString(2, normalize(value));
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readGang(result)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Optional<GangMember>> member(UUID playerId) {
        return database.submitIdempotent(connection -> memberByPlayer(connection, playerId));
    }

    public CompletableFuture<List<GangMember>> members(UUID gangId) {
        return database.submitIdempotent(connection -> {
            List<GangMember> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_gang_members WHERE gang_id=? ORDER BY joined_at,player_uuid")) {
                statement.setString(1, gangId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readMember(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<List<GangRank>> ranks(UUID gangId) {
        return database.submitIdempotent(connection -> ranks(connection, gangId));
    }

    public CompletableFuture<Optional<GangRank>> rank(UUID rankId) {
        return database.submitIdempotent(connection -> rankById(connection, rankId));
    }

    public CompletableFuture<GangInvite> invite(UUID gangId, UUID playerId, UUID invitedBy, long expiresAt, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            if (memberByPlayer(active, playerId).isPresent()) throw new Conflict("Player already belongs to a gang");
            if (gang.memberCount() >= gang.memberLimit()) throw new Conflict("Gang is full");
            try (PreparedStatement revoke = active.prepareStatement("UPDATE rp_gang_invites SET status='REVOKED' "
                    + "WHERE gang_id=? AND player_uuid=? AND status='PENDING'")) {
                revoke.setString(1, gangId.toString());
                revoke.setString(2, playerId.toString());
                revoke.executeUpdate();
            }
            GangInvite invite = new GangInvite(UUID.randomUUID(), gangId, playerId, invitedBy, now, expiresAt,
                    GangInvite.Status.PENDING);
            try (PreparedStatement statement = active.prepareStatement("INSERT INTO rp_gang_invites("
                    + "invite_id,gang_id,player_uuid,invited_by,created_at,expires_at,status) VALUES(?,?,?,?,?,?,?)")) {
                statement.setString(1, invite.id().toString());
                statement.setString(2, gangId.toString());
                statement.setString(3, playerId.toString());
                statement.setString(4, invitedBy.toString());
                statement.setLong(5, now);
                statement.setLong(6, expiresAt);
                statement.setString(7, invite.status().name());
                statement.executeUpdate();
            }
            audit(active, gangId, invitedBy, "invite", "player", playerId.toString(), "", "pending", true,
                    "", "invite=" + invite.id(), now);
            return invite;
        }));
    }

    public CompletableFuture<List<GangInvite>> invites(UUID playerId, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            expireInvites(active, now);
            List<GangInvite> result = new ArrayList<>();
            try (PreparedStatement statement = active.prepareStatement("SELECT * FROM rp_gang_invites "
                    + "WHERE player_uuid=? AND status='PENDING' AND expires_at>? ORDER BY created_at DESC")) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, now);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readInvite(rows));
                }
            }
            return List.copyOf(result);
        }));
    }

    public CompletableFuture<GangOperationResult> respondInvite(UUID inviteId, UUID playerId, boolean accept,
                                                                 long now) {
        return database.submit(connection -> transaction(connection, active -> {
            GangInvite invite = invite(active, inviteId).orElseThrow(() -> new Conflict("Invite does not exist"));
            if (!invite.playerId().equals(playerId)) throw new Conflict("Invite belongs to another player");
            if (invite.status() != GangInvite.Status.PENDING) throw new Conflict("Invite is no longer pending");
            if (invite.expiresAt() <= now) {
                setInviteStatus(active, inviteId, GangInvite.Status.EXPIRED);
                return GangOperationResult.failure("Invite has expired");
            }
            if (!accept) {
                setInviteStatus(active, inviteId, GangInvite.Status.DENIED);
                audit(active, invite.gangId(), playerId, "invite_deny", "invite", inviteId.toString(),
                        "pending", "denied", true, "", "", now);
                return GangOperationResult.success("Gang invitation declined");
            }
            join(active, invite.gangId(), playerId, now);
            setInviteStatus(active, inviteId, GangInvite.Status.ACCEPTED);
            revokeOtherInvites(active, playerId, inviteId);
            audit(active, invite.gangId(), playerId, "invite_accept", "invite", inviteId.toString(),
                    "pending", "accepted", true, "", "", now);
            return GangOperationResult.success("You joined the gang successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> joinOpen(UUID gangId, UUID playerId, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            if (gang.joinMode() != GangJoinMode.OPEN) throw new Conflict("Gang is not open");
            join(active, gangId, playerId, now);
            revokeOtherInvites(active, playerId, null);
            audit(active, gangId, playerId, "open_join", "player", playerId.toString(), "", "member", true,
                    "", "", now);
            return GangOperationResult.success("You joined the gang successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> removeMember(UUID gangId, UUID playerId, UUID actorId,
                                                                boolean voluntary, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            if (gang.ownerId().equals(playerId)) throw new Conflict("Owner must transfer ownership or disband");
            try (PreparedStatement statement = active.prepareStatement(
                    "DELETE FROM rp_gang_members WHERE gang_id=? AND player_uuid=?")) {
                statement.setString(1, gangId.toString());
                statement.setString(2, playerId.toString());
                if (statement.executeUpdate() != 1) throw new Conflict("Player is not a gang member");
            }
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET member_count=member_count-1,"
                    + "version=version+1,updated_at=? WHERE gang_id=? AND member_count>0")) {
                statement.setLong(1, now);
                statement.setString(2, gangId.toString());
                statement.executeUpdate();
            }
            audit(active, gangId, actorId, voluntary ? "leave" : "kick", "player", playerId.toString(),
                    "member", "", true, "", "", now);
            return GangOperationResult.success(voluntary ? "You left the gang successfully"
                    : "The member was removed from the gang");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> transferOwnership(UUID gangId, UUID oldOwner, UUID newOwner,
                                                                     long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            if (!gang.ownerId().equals(oldOwner)) throw new Conflict("Actor is not the gang owner");
            GangMember oldMember = memberInGang(active, gangId, oldOwner);
            GangMember newMember = memberInGang(active, gangId, newOwner);
            UUID ownerRank = systemRank(active, gangId, "owner").id();
            UUID coLeaderRank = systemRank(active, gangId, "co_leader").id();
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET owner_uuid=?,"
                    + "version=version+1,updated_at=? WHERE gang_id=? AND owner_uuid=? AND enabled=TRUE")) {
                statement.setString(1, newOwner.toString());
                statement.setLong(2, now);
                statement.setString(3, gangId.toString());
                statement.setString(4, oldOwner.toString());
                if (statement.executeUpdate() != 1) throw new Conflict("Ownership changed concurrently");
            }
            updateMemberRank(active, oldMember.playerId(), coLeaderRank, oldMember.version());
            updateMemberRank(active, newMember.playerId(), ownerRank, newMember.version());
            audit(active, gangId, oldOwner, "transfer_ownership", "player", newOwner.toString(),
                    oldOwner.toString(), newOwner.toString(), true, "", "", now);
            return GangOperationResult.success("Gang ownership was transferred successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> disband(UUID gangId, UUID actorId, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET enabled=FALSE,"
                    + "member_count=0,version=version+1,updated_at=? WHERE gang_id=? AND enabled=TRUE")) {
                statement.setLong(1, now);
                statement.setString(2, gangId.toString());
                if (statement.executeUpdate() != 1) throw new Conflict("Gang was already disbanded");
            }
            execute(active, "DELETE FROM rp_gang_members WHERE gang_id=?", gangId);
            execute(active, "UPDATE rp_gang_invites SET status='REVOKED' WHERE gang_id=? AND status='PENDING'", gangId);
            execute(active, "UPDATE rp_gang_boosters SET enabled=FALSE WHERE gang_id=?", gangId);
            execute(active, "UPDATE rp_gang_missions SET state='CANCELLED',updated_at=" + now
                    + " WHERE gang_id=? AND state IN ('ACTIVE','COMPLETED')", gangId);
            audit(active, gangId, actorId, "disband", "gang", gangId.toString(), gang.name(), "disabled", true,
                    "", "bank=" + gang.bankBalance(), now);
            return GangOperationResult.success("The gang was disbanded successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangRank> createRank(UUID gangId, UUID actorId, String displayName, int priority,
                                                   String color, Set<GangPermission> permissions, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            enabledGang(active, gangId);
            GangRank rank = new GangRank(UUID.randomUUID(), gangId, "", displayName, priority, color, false,
                    permissions, now, now);
            try {
                insertRank(active, rank);
            } catch (SQLException ex) {
                if (constraint(ex)) throw new Conflict("Rank priority is already in use", ex);
                throw ex;
            }
            audit(active, gangId, actorId, "rank_create", "rank", rank.id().toString(), "", displayName,
                    true, "", "priority=" + priority, now);
            return rank;
        }));
    }

    public CompletableFuture<GangOperationResult> editRank(UUID gangId, UUID rankId, UUID actorId,
                                                            String displayName, int priority, String color, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            GangRank rank = rankInGang(active, gangId, rankId);
            if (rank.owner() && priority != rank.priority()) throw new Conflict("Owner priority cannot be changed");
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gang_ranks SET display_name=?,"
                    + "priority=?,color=?,updated_at=? WHERE rank_id=? AND gang_id=?")) {
                statement.setString(1, displayName);
                statement.setInt(2, priority);
                statement.setString(3, color);
                statement.setLong(4, now);
                statement.setString(5, rankId.toString());
                statement.setString(6, gangId.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                if (constraint(ex)) throw new Conflict("Rank priority is already in use", ex);
                throw ex;
            }
            audit(active, gangId, actorId, "rank_edit", "rank", rankId.toString(), rank.displayName(),
                    displayName, true, "", "priority=" + priority, now);
            return GangOperationResult.success("The gang rank was updated successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> setRankPermission(UUID gangId, UUID rankId, UUID actorId,
                                                                     GangPermission permission, boolean enabled,
                                                                     long now) {
        return database.submit(connection -> transaction(connection, active -> {
            GangRank rank = rankInGang(active, gangId, rankId);
            if (rank.owner() && !enabled) throw new Conflict("Owner permissions cannot be removed");
            try (PreparedStatement update = active.prepareStatement("UPDATE rp_gang_rank_permissions SET enabled=? "
                    + "WHERE rank_id=? AND permission=?")) {
                update.setBoolean(1, enabled);
                update.setString(2, rankId.toString());
                update.setString(3, permission.key());
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = active.prepareStatement("INSERT INTO rp_gang_rank_permissions("
                            + "rank_id,permission,enabled) VALUES(?,?,?)")) {
                        insert.setString(1, rankId.toString());
                        insert.setString(2, permission.key());
                        insert.setBoolean(3, enabled);
                        insert.executeUpdate();
                    }
                }
            }
            audit(active, gangId, actorId, "rank_permission", "rank", rankId.toString(),
                    permission.key() + "=" + !enabled, permission.key() + "=" + enabled, true, "", "", now);
            return GangOperationResult.success("The gang rank permission was updated successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> deleteRank(UUID gangId, UUID rankId, UUID fallbackRankId,
                                                              UUID actorId, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            GangRank rank = rankInGang(active, gangId, rankId);
            if (rank.owner()) throw new Conflict("Owner rank cannot be deleted");
            if (rank.defaultRank()) throw new Conflict("Default ranks cannot be deleted");
            GangRank fallback = rankInGang(active, gangId, fallbackRankId);
            if (fallback.id().equals(rank.id())) throw new Conflict("Fallback rank must be different");
            try (PreparedStatement statement = active.prepareStatement(
                    "UPDATE rp_gang_members SET rank_id=?,version=version+1 WHERE gang_id=? AND rank_id=?")) {
                statement.setString(1, fallback.id().toString());
                statement.setString(2, gangId.toString());
                statement.setString(3, rank.id().toString());
                statement.executeUpdate();
            }
            execute(active, "DELETE FROM rp_gang_rank_permissions WHERE rank_id=?", rank.id());
            execute(active, "DELETE FROM rp_gang_ranks WHERE rank_id=?", rank.id());
            audit(active, gangId, actorId, "rank_delete", "rank", rankId.toString(), rank.displayName(),
                    "deleted", true, "", "fallback=" + fallbackRankId, now);
            return GangOperationResult.success("The gang rank was deleted successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> setMemberRank(UUID gangId, UUID playerId, UUID rankId,
                                                                 UUID actorId, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            if (gang.ownerId().equals(playerId)) throw new Conflict("Owner rank changes require ownership transfer");
            GangMember member = memberInGang(active, gangId, playerId);
            GangRank rank = rankInGang(active, gangId, rankId);
            if (rank.owner()) throw new Conflict("Owner rank requires ownership transfer");
            updateMemberRank(active, playerId, rankId, member.version());
            audit(active, gangId, actorId, "member_rank", "player", playerId.toString(),
                    member.rankId().toString(), rankId.toString(), true, "", "", now);
            return GangOperationResult.success("The member's gang rank was updated successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangBankTransaction> changeBank(UUID gangId, UUID playerId, BigDecimal amount,
                                                               GangBankTransaction.Type type, String reason,
                                                               String operationKey, BigDecimal capacity,
                                                               BigDecimal dailyWithdrawalLimit, String periodKey,
                                                               long now) {
        return database.submitIdempotent(connection -> transaction(connection, active -> {
            Optional<GangBankTransaction> previous = bankTransactionByOperation(active, operationKey);
            if (previous.isPresent()) return previous.get();
            Gang gang = enabledGang(active, gangId);
            BigDecimal delta = amount.setScale(2, java.math.RoundingMode.HALF_UP);
            if (delta.signum() == 0) throw new Conflict("Bank amount cannot be zero");
            BigDecimal before = gang.bankBalance();
            BigDecimal after = before.add(delta);
            if (after.signum() < 0) throw new Conflict("Gang bank has insufficient funds");
            if (capacity.signum() > 0 && after.compareTo(capacity) > 0) throw new Conflict("Gang bank capacity exceeded");
            if (delta.signum() < 0 && playerId != null && dailyWithdrawalLimit.signum() > 0) {
                BigDecimal used = withdrawalUsage(active, gangId, playerId, periodKey);
                if (used.add(delta.abs()).compareTo(dailyWithdrawalLimit) > 0) {
                    throw new Conflict("Daily withdrawal limit exceeded");
                }
                setWithdrawalUsage(active, gangId, playerId, periodKey, used.add(delta.abs()));
            }
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET bank_balance=?,"
                    + "version=version+1,updated_at=? WHERE gang_id=? AND bank_balance=? AND enabled=TRUE")) {
                statement.setBigDecimal(1, after);
                statement.setLong(2, now);
                statement.setString(3, gangId.toString());
                statement.setBigDecimal(4, before);
                if (statement.executeUpdate() != 1) throw new Conflict("Gang bank changed concurrently; retry safely");
            }
            GangBankTransaction transaction = new GangBankTransaction(UUID.randomUUID(), gangId, playerId,
                    delta, before, after, type, reason, now, operationKey);
            insertBankTransaction(active, transaction);
            audit(active, gangId, playerId, "bank_" + type.name().toLowerCase(Locale.ROOT), "bank", gangId.toString(),
                    before.toPlainString(), after.toPlainString(), true, reason, "operation=" + operationKey, now);
            return transaction;
        }));
    }

    public CompletableFuture<List<GangBankTransaction>> bankHistory(UUID gangId, int limit) {
        return database.submitIdempotent(connection -> {
            List<GangBankTransaction> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_bank_transactions "
                    + "WHERE gang_id=? ORDER BY created_at DESC,transaction_id DESC LIMIT ?")) {
                statement.setString(1, gangId.toString());
                statement.setInt(2, Math.max(1, Math.min(200, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readBankTransaction(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<GangUpgradeState> purchaseUpgrade(UUID gangId, UUID actorId,
                                                                GangConfig.UpgradeDefinition definition,
                                                                String operationKey, int maximumMemberLimit, long now) {
        return database.submitIdempotent(connection -> transaction(connection, active -> {
            Optional<String> claimed = operationClaim(active, operationKey);
            if (claimed.isPresent()) {
                int tier = Integer.parseInt(claimed.get());
                return new GangUpgradeState(gangId, definition.id(), tier, now, actorId);
            }
            Gang gang = enabledGang(active, gangId);
            int currentTier = upgradeTier(active, gangId, definition.id());
            int nextTier = currentTier + 1;
            if (nextTier > definition.maxTier()) throw new Conflict("Upgrade is already at maximum tier");
            for (GangConfig.UpgradePrerequisite prerequisite : definition.prerequisites()) {
                if (upgradeTier(active, gangId, prerequisite.upgradeId()) < prerequisite.tier()) {
                    throw new Conflict("Missing upgrade prerequisite: " + prerequisite.upgradeId());
                }
            }
            GangConfig.UpgradeTier tier = definition.tier(nextTier);
            if (definition.costType() == GangConfig.CostType.POINTS) {
                try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET points=points-?,"
                        + "version=version+1,updated_at=? WHERE gang_id=? AND points>=? AND enabled=TRUE")) {
                    long cost = tier.cost().longValueExact();
                    statement.setLong(1, cost);
                    statement.setLong(2, now);
                    statement.setString(3, gangId.toString());
                    statement.setLong(4, cost);
                    if (statement.executeUpdate() != 1) throw new Conflict("Gang has insufficient points");
                }
            } else {
                BigDecimal after = gang.bankBalance().subtract(tier.cost());
                if (after.signum() < 0) throw new Conflict("Gang bank has insufficient funds");
                try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET bank_balance=?,"
                        + "version=version+1,updated_at=? WHERE gang_id=? AND bank_balance=? AND enabled=TRUE")) {
                    statement.setBigDecimal(1, after);
                    statement.setLong(2, now);
                    statement.setString(3, gangId.toString());
                    statement.setBigDecimal(4, gang.bankBalance());
                    if (statement.executeUpdate() != 1) throw new Conflict("Gang bank changed concurrently");
                }
                insertBankTransaction(active, new GangBankTransaction(UUID.randomUUID(), gangId, actorId,
                        tier.cost().negate(), gang.bankBalance(), after, GangBankTransaction.Type.UPGRADE_PURCHASE,
                        definition.id() + " tier " + nextTier, now, operationKey));
            }
            upsertUpgrade(active, gangId, definition.id(), nextTier, actorId, now);
            BigDecimal capacityEffect = tier.effects().get("member_capacity");
            if (capacityEffect != null && capacityEffect.signum() > 0) {
                int increase = capacityEffect.intValueExact();
                if (gang.memberLimit() + increase > maximumMemberLimit) {
                    throw new Conflict("Upgrade would exceed the configured member limit");
                }
                try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET member_limit=member_limit+? "
                        + "WHERE gang_id=?")) {
                    statement.setInt(1, increase);
                    statement.setString(2, gangId.toString());
                    statement.executeUpdate();
                }
            }
            insertOperationClaim(active, operationKey, gangId, "upgrade_purchase", String.valueOf(nextTier), now);
            audit(active, gangId, actorId, "upgrade_purchase", "upgrade", definition.id(),
                    String.valueOf(currentTier), String.valueOf(nextTier), true, "", "operation=" + operationKey, now);
            return new GangUpgradeState(gangId, definition.id(), nextTier, now, actorId);
        }));
    }

    public CompletableFuture<Map<String, Integer>> upgrades(UUID gangId) {
        return database.submitIdempotent(connection -> {
            Map<String, Integer> result = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT upgrade_id,tier FROM rp_gang_upgrades WHERE gang_id=?")) {
                statement.setString(1, gangId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getString(1), rows.getInt(2));
                }
            }
            return Map.copyOf(result);
        });
    }

    public CompletableFuture<ContributionResult> recordContribution(UUID playerId,
            GangConfig.ContributionType type, BigDecimal amount, BigDecimal awardedXp, String targetKey,
            String operationKey, GangConfig config, String periodKey, long now) {
        return database.submitIdempotent(connection -> transaction(connection, active -> {
            Optional<String> claimed = operationClaim(active, operationKey);
            if (claimed.isPresent()) return ContributionResult.duplicateResult();
            Optional<GangMember> membership = memberByPlayer(active, playerId);
            if (membership.isEmpty()) return ContributionResult.notMember();
            UUID gangId = membership.get().gangId();
            Gang gang = enabledGang(active, gangId);
            ensureMemberStatistics(active, gangId, playerId, now);
            ContributionColumns columns = ContributionColumns.forType(type, amount);
            updateStatistics(active, "rp_gang_statistics", "gang_id", gangId.toString(), columns, awardedXp, now);
            updateStatistics(active, "rp_gang_member_statistics", "gang_id=? AND player_uuid=?",
                    List.of(gangId.toString(), playerId.toString()), columns, awardedXp, now);
            updatePeriodStatistics(active, gangId, columns, awardedXp, periodKey, now);
            ProgressionState progression = applyXp(active, gang, awardedXp, config, now);
            List<String> completed = applyMissionProgress(active, gangId, playerId, type, targetKey, amount,
                    config, periodKey, now);
            insertOperationClaim(active, operationKey, gangId, "contribution", type.name(), now);
            return new ContributionResult(true, false, gangId, progression.oldLevel(), progression.newLevel(),
                    awardedXp, completed);
        }));
    }

    public CompletableFuture<GangStatistics> statistics(UUID gangId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_gang_statistics WHERE gang_id=?")) {
                statement.setString(1, gangId.toString());
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return new GangStatistics(gangId, 0L, BigDecimal.ZERO, BigDecimal.ZERO,
                            0L, 0L, 0L, 0L);
                    return readStatistics(row);
                }
            }
        });
    }

    public CompletableFuture<List<GangMemberStatistics>> memberStatistics(UUID gangId) {
        return database.submitIdempotent(connection -> {
            List<GangMemberStatistics> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_member_statistics "
                    + "WHERE gang_id=? ORDER BY gang_xp_earned DESC,blocks_mined DESC")) {
                statement.setString(1, gangId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readMemberStatistics(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<GangBooster> activateBooster(UUID gangId, GangBooster.Type type,
                                                           BigDecimal multiplier, long startsAt, long expiresAt,
                                                           UUID actorId, String source, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            enabledGang(active, gangId);
            GangBooster booster = new GangBooster(UUID.randomUUID(), gangId, type, multiplier, startsAt,
                    expiresAt, actorId, true, source, now);
            try (PreparedStatement statement = active.prepareStatement("INSERT INTO rp_gang_boosters("
                    + "booster_id,gang_id,booster_type,multiplier,starts_at,expires_at,activated_by,enabled,source,created_at) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, booster.id().toString());
                statement.setString(2, gangId.toString());
                statement.setString(3, type.name());
                statement.setBigDecimal(4, multiplier);
                statement.setLong(5, startsAt);
                statement.setLong(6, expiresAt);
                nullableUuid(statement, 7, actorId);
                statement.setBoolean(8, true);
                statement.setString(9, source);
                statement.setLong(10, now);
                statement.executeUpdate();
            }
            audit(active, gangId, actorId, "booster_activate", "booster", booster.id().toString(), "",
                    type + " x" + multiplier, true, "", "source=" + source, now);
            return booster;
        }));
    }

    public CompletableFuture<List<GangBooster>> activeBoosters(long now) {
        return database.submitIdempotent(connection -> {
            List<GangBooster> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_boosters "
                    + "WHERE enabled=TRUE AND expires_at>?")) {
                statement.setLong(1, now);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readBooster(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Void> expireBoosters(long now) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_gang_boosters SET enabled=FALSE WHERE enabled=TRUE AND expires_at<=?")) {
                statement.setLong(1, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<GangMissionState>> missions(UUID gangId, GangConfig config, String periodKey,
                                                               long now) {
        return database.submit(connection -> transaction(connection, active -> {
            enabledGang(active, gangId);
            List<GangMissionState> result = new ArrayList<>();
            for (GangConfig.MissionDefinition definition : config.missions().values()) {
                String effectivePeriod = missionPeriod(active, definition, periodKey, now);
                String instanceId = missionInstanceId(gangId, definition.id(), effectivePeriod);
                GangMissionState state = mission(active, instanceId).orElseGet(() -> {
                    GangMissionState created = new GangMissionState(instanceId, gangId, definition.id(), effectivePeriod,
                            BigDecimal.ZERO, definition.target(), GangMissionState.State.ACTIVE, null, null, now);
                    try { insertMission(active, created); }
                    catch (SQLException ex) { throw new DatabaseManager.DatabaseException(ex); }
                    return created;
                });
                result.add(state);
            }
            return List.copyOf(result);
        }));
    }

    public CompletableFuture<GangOperationResult> claimMission(UUID gangId, String missionId, String periodKey,
                                                                UUID actorId, GangConfig config, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            GangConfig.MissionDefinition definition = Optional.ofNullable(config.missions().get(missionId))
                    .orElseThrow(() -> new Conflict("Unknown mission"));
            String instanceId = missionInstanceId(gangId, missionId,
                    missionPeriod(active, definition, periodKey, now));
            GangMissionState state = mission(active, instanceId).orElseThrow(() -> new Conflict("Mission is not active"));
            if (state.state() != GangMissionState.State.COMPLETED) throw new Conflict("Mission is not ready to claim");
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gang_missions SET state='CLAIMED',"
                    + "claimed_at=?,updated_at=? WHERE mission_instance_id=? AND state='COMPLETED'")) {
                statement.setLong(1, now);
                statement.setLong(2, now);
                statement.setString(3, instanceId);
                if (statement.executeUpdate() != 1) throw new Conflict("Mission was claimed concurrently");
            }
            GangConfig.MissionReward reward = definition.reward();
            applyMissionReward(active, gangId, actorId, instanceId, reward, config, now);
            audit(active, gangId, actorId, "mission_claim", "mission", instanceId, "completed", "claimed",
                    true, "", "", now);
            return GangOperationResult.success("The gang mission reward was claimed successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<List<GangLeaderboardEntry>> leaderboard(String category, int limit) {
        return database.submitIdempotent(connection -> leaderboard(connection, category, limit));
    }

    public CompletableFuture<GangSeason> createSeason(String seasonId, long startsAt, long endsAt,
                                                       List<String> categories, String rewardPlan, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            if (startsAt >= endsAt) throw new Conflict("Season end must be after its start");
            GangSeason.State state = now >= startsAt ? GangSeason.State.ACTIVE : GangSeason.State.SCHEDULED;
            try (PreparedStatement statement = active.prepareStatement("INSERT INTO rp_gang_seasons("
                    + "season_id,starts_at,ends_at,tracked_categories,reward_plan,state,created_at,finalized_at) "
                    + "VALUES(?,?,?,?,?,?,?,NULL)")) {
                statement.setString(1, seasonId);
                statement.setLong(2, startsAt);
                statement.setLong(3, endsAt);
                statement.setString(4, String.join(",", categories));
                statement.setString(5, rewardPlan == null ? "" : rewardPlan);
                statement.setString(6, state.name());
                statement.setLong(7, now);
                statement.executeUpdate();
            }
            return new GangSeason(seasonId, startsAt, endsAt, categories, rewardPlan, state, now, null);
        }));
    }

    public CompletableFuture<List<GangSeasonResult>> finalizeSeason(String seasonId, int winnerLimit, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            GangSeason season = season(active, seasonId).orElseThrow(() -> new Conflict("Season does not exist"));
            if (season.state() == GangSeason.State.FINALIZED) return seasonResults(active, seasonId);
            if (now < season.endsAt()) throw new Conflict("Season has not ended");
            List<GangSeasonResult> frozen = new ArrayList<>();
            for (String category : season.categories()) {
                List<GangLeaderboardEntry> entries = leaderboard(active, category, winnerLimit,
                        "season", seasonId);
                for (GangLeaderboardEntry entry : entries) {
                    GangSeasonResult result = new GangSeasonResult(seasonId, category, entry.position(), entry.gangId(),
                            entry.gangName(), entry.value(), season.rewardPlan(), "FROZEN", now);
                    insertSeasonResult(active, result);
                    frozen.add(result);
                }
            }
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gang_seasons SET state='FINALIZED',"
                    + "finalized_at=? WHERE season_id=? AND state<>'FINALIZED'")) {
                statement.setLong(1, now);
                statement.setString(2, seasonId);
                if (statement.executeUpdate() != 1) throw new Conflict("Season finalized concurrently");
            }
            return List.copyOf(frozen);
        }));
    }

    public CompletableFuture<List<GangSeasonResult>> seasonResults(String seasonId) {
        return database.submitIdempotent(connection -> seasonResults(connection, seasonId));
    }

    public CompletableFuture<List<GangSeasonResult>> pendingSeasonRewards(int limit) {
        return database.submitIdempotent(connection -> {
            List<GangSeasonResult> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_season_results "
                    + "WHERE reward_state='FROZEN' ORDER BY recorded_at LIMIT ?")) {
                statement.setInt(1, Math.max(1, Math.min(1000, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new GangSeasonResult(rows.getString("season_id"),
                            rows.getString("category"), rows.getInt("position"),
                            UUID.fromString(rows.getString("gang_id")), rows.getString("gang_name"),
                            rows.getBigDecimal("value"), rows.getString("frozen_reward"),
                            rows.getString("reward_state"), rows.getLong("recorded_at")));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Void> markSeasonRewardState(GangSeasonResult result, String state) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gang_season_results SET "
                    + "reward_state=? WHERE season_id=? AND category=? AND position=?")) {
                statement.setString(1, state);
                statement.setString(2, result.seasonId());
                statement.setString(3, result.category());
                statement.setInt(4, result.position());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<MissionRewardClaim>> missionRewardClaims(int limit) {
        return database.submitIdempotent(connection -> {
            List<MissionRewardClaim> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT operation_key,gang_id,result_payload "
                    + "FROM rp_gang_operation_claims WHERE operation_type='mission_reward' ORDER BY created_at LIMIT ?")) {
                statement.setInt(1, Math.max(1, Math.min(1000, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new MissionRewardClaim(rows.getString(1),
                            UUID.fromString(rows.getString(2)), rows.getString(3)));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<List<Gang>> search(String query, int limit) {
        return database.submitIdempotent(connection -> {
            List<Gang> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gangs WHERE "
                    + "normalized_name LIKE ? OR normalized_tag LIKE ? ORDER BY enabled DESC,normalized_name LIMIT ?")) {
                statement.setString(1, "%" + normalize(query) + "%");
                statement.setString(2, "%" + normalize(query) + "%");
                statement.setInt(3, Math.max(1, Math.min(200, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readGang(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<List<GangAuditEntry>> audit(UUID gangId, int limit) {
        return database.submitIdempotent(connection -> {
            List<GangAuditEntry> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_audit WHERE "
                    + "gang_id=? ORDER BY created_at DESC,audit_id DESC LIMIT ?")) {
                statement.setString(1, gangId.toString());
                statement.setInt(2, Math.max(1, Math.min(200, limit)));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(readAudit(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<GangOperationResult> editGang(UUID gangId, UUID actorId, Edit edit, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            String sql = switch (edit.field()) {
                case NAME -> "UPDATE rp_gangs SET name=?,normalized_name=?,version=version+1,updated_at=? WHERE gang_id=?";
                case TAG -> "UPDATE rp_gangs SET tag=?,normalized_tag=?,version=version+1,updated_at=? WHERE gang_id=?";
                case DESCRIPTION -> "UPDATE rp_gangs SET description=?,version=version+1,updated_at=? WHERE gang_id=?";
                case COLOR -> "UPDATE rp_gangs SET color=?,version=version+1,updated_at=? WHERE gang_id=?";
                case MOTD -> "UPDATE rp_gangs SET motd=?,version=version+1,updated_at=? WHERE gang_id=?";
                case JOIN_MODE -> "UPDATE rp_gangs SET join_mode=?,version=version+1,updated_at=? WHERE gang_id=?";
            };
            try (PreparedStatement statement = active.prepareStatement(sql)) {
                statement.setString(1, edit.value());
                int index = 2;
                if (edit.field() == EditField.NAME || edit.field() == EditField.TAG) {
                    statement.setString(index++, normalize(edit.value()));
                }
                statement.setLong(index++, now);
                statement.setString(index, gangId.toString());
                statement.executeUpdate();
            } catch (SQLException ex) {
                if (constraint(ex)) throw new Conflict("Gang name or tag is already in use", ex);
                throw ex;
            }
            audit(active, gangId, actorId, "edit_" + edit.field().name().toLowerCase(Locale.ROOT), "gang",
                    gangId.toString(), edit.before(gang), edit.value(), true, "", "", now);
            return GangOperationResult.success("The gang settings were updated successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> setHome(UUID gangId, UUID actorId, Gang.GangHome home, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            enabledGang(active, gangId);
            try (PreparedStatement statement = active.prepareStatement("UPDATE rp_gangs SET home_world=?,home_x=?,"
                    + "home_y=?,home_z=?,home_yaw=?,home_pitch=?,version=version+1,updated_at=? WHERE gang_id=?")) {
                statement.setString(1, home.world());
                statement.setDouble(2, home.x());
                statement.setDouble(3, home.y());
                statement.setDouble(4, home.z());
                statement.setFloat(5, home.yaw());
                statement.setFloat(6, home.pitch());
                statement.setLong(7, now);
                statement.setString(8, gangId.toString());
                statement.executeUpdate();
            }
            audit(active, gangId, actorId, "set_home", "gang", gangId.toString(), "", home.world(), true,
                    "", "", now);
            return GangOperationResult.success("The gang home was set successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> setChatToggle(UUID playerId, boolean enabled) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_gang_members SET chat_enabled=?,version=version+1 WHERE player_uuid=?")) {
                statement.setBoolean(1, enabled);
                statement.setString(2, playerId.toString());
                return statement.executeUpdate() == 1 ? GangOperationResult.success("Gang chat updated")
                        : GangOperationResult.failure("You are not currently in a gang");
            }
        });
    }

    public CompletableFuture<GangOperationResult> forceAddMember(UUID gangId, UUID playerId, UUID actorId, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            enabledGang(active, gangId);
            join(active, gangId, playerId, now);
            revokeOtherInvites(active, playerId, null);
            audit(active, gangId, actorId, "admin_add_member", "player", playerId.toString(), "", "member",
                    true, "", "", now);
            return GangOperationResult.success("The member was added to the gang successfully");
        })).exceptionally(GangRepository::operationFailure);
    }

    public CompletableFuture<GangOperationResult> adjustProgression(UUID gangId, UUID actorId,
            AdminNumericField field, BigDecimal value, boolean additive, long now) {
        return database.submit(connection -> transaction(connection, active -> {
            Gang gang = enabledGang(active, gangId);
            BigDecimal minimum = field == AdminNumericField.LEVEL ? BigDecimal.ONE : BigDecimal.ZERO;
            if (!additive && value.compareTo(minimum) < 0) throw new Conflict(field.column() + " is below its minimum");
            String expression = additive ? field.column() + "+?" : "?";
            String sql = "UPDATE rp_gangs SET " + field.column() + "=" + expression
                    + ",version=version+1,updated_at=? WHERE gang_id=? AND enabled=TRUE"
                    + (additive ? " AND " + field.column() + "+?>=" + minimum.toPlainString() : "");
            try (PreparedStatement statement = active.prepareStatement(sql)) {
                if (field == AdminNumericField.XP) statement.setBigDecimal(1, value);
                else statement.setLong(1, value.longValueExact());
                statement.setLong(2, now);
                statement.setString(3, gangId.toString());
                if (additive) {
                    if (field == AdminNumericField.XP) statement.setBigDecimal(4, value);
                    else statement.setLong(4, value.longValueExact());
                }
                if (statement.executeUpdate() != 1) throw new Conflict(field.column() + " adjustment would underflow");
            }
            audit(active, gangId, actorId, "admin_" + (additive ? "add_" : "set_") + field.column(),
                    "gang", gangId.toString(), field.value(gang), value.toPlainString(), true, "", "", now);
            return GangOperationResult.success("Gang " + field.column() + " updated");
        })).exceptionally(GangRepository::operationFailure);
    }

    public enum AdminNumericField {
        LEVEL("level"), XP("xp"), POINTS("points");
        private final String column;
        AdminNumericField(String column) { this.column = column; }
        String column() { return column; }
        String value(Gang gang) {
            return switch (this) {
                case LEVEL -> String.valueOf(gang.level());
                case XP -> gang.xp().toPlainString();
                case POINTS -> String.valueOf(gang.points());
            };
        }
    }

    public enum EditField { NAME, TAG, DESCRIPTION, COLOR, MOTD, JOIN_MODE }
    public record Edit(EditField field, String value) {
        private String before(Gang gang) {
            return switch (field) {
                case NAME -> gang.name();
                case TAG -> gang.tag();
                case DESCRIPTION -> gang.description();
                case COLOR -> gang.color();
                case MOTD -> gang.motd();
                case JOIN_MODE -> gang.joinMode().name();
            };
        }
    }

    public record ContributionResult(boolean applied, boolean duplicate, UUID gangId, int oldLevel, int newLevel,
                                     BigDecimal gangXp, List<String> completedMissions) {
        public ContributionResult { completedMissions = List.copyOf(completedMissions); }
        static ContributionResult duplicateResult() {
            return new ContributionResult(false, true, null, 0, 0, BigDecimal.ZERO, List.of());
        }
        static ContributionResult notMember() {
            return new ContributionResult(false, false, null, 0, 0, BigDecimal.ZERO, List.of());
        }
    }

    private static void join(Connection connection, UUID gangId, UUID playerId, long now) throws SQLException {
        if (memberByPlayer(connection, playerId).isPresent()) throw new Conflict("Player already belongs to a gang");
        try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gangs SET member_count=member_count+1,"
                + "version=version+1,updated_at=? WHERE gang_id=? AND enabled=TRUE AND member_count<member_limit")) {
            statement.setLong(1, now);
            statement.setString(2, gangId.toString());
            if (statement.executeUpdate() != 1) throw new Conflict("Gang is full or unavailable");
        }
        GangRank recruit = systemRank(connection, gangId, "recruit");
        try {
            insertMember(connection, new GangMember(playerId, gangId, recruit.id(), now, now, false, 1L));
        } catch (SQLException ex) {
            if (constraint(ex)) throw new Conflict("Player joined another gang concurrently", ex);
            throw ex;
        }
    }

    private static Gang enabledGang(Connection connection, UUID gangId) throws SQLException {
        Gang gang = gangById(connection, gangId).orElseThrow(() -> new Conflict("Gang does not exist"));
        if (!gang.enabled()) throw new Conflict("Gang is disabled");
        return gang;
    }

    private static Optional<Gang> gangById(Connection connection, UUID gangId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gangs WHERE gang_id=?")) {
            statement.setString(1, gangId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readGang(result)) : Optional.empty();
            }
        }
    }

    private static Optional<GangMember> memberByPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_gang_members WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readMember(result)) : Optional.empty();
            }
        }
    }

    private static GangMember memberInGang(Connection connection, UUID gangId, UUID playerId) throws SQLException {
        GangMember member = memberByPlayer(connection, playerId)
                .orElseThrow(() -> new Conflict("Player is not a gang member"));
        if (!member.gangId().equals(gangId)) throw new Conflict("Player belongs to another gang");
        return member;
    }

    private static List<GangRank> ranks(Connection connection, UUID gangId) throws SQLException {
        List<GangRank> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_gang_ranks WHERE gang_id=? ORDER BY priority DESC")) {
            statement.setString(1, gangId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readRank(connection, rows));
            }
        }
        return List.copyOf(result);
    }

    private static Optional<GangRank> rankById(Connection connection, UUID rankId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_ranks WHERE rank_id=?")) {
            statement.setString(1, rankId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readRank(connection, row)) : Optional.empty();
            }
        }
    }

    private static GangRank rankInGang(Connection connection, UUID gangId, UUID rankId) throws SQLException {
        GangRank rank = rankById(connection, rankId).orElseThrow(() -> new Conflict("Rank does not exist"));
        if (!rank.gangId().equals(gangId)) throw new Conflict("Rank belongs to another gang");
        return rank;
    }

    private static GangRank systemRank(Connection connection, UUID gangId, String systemKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_gang_ranks WHERE gang_id=? AND system_key=?")) {
            statement.setString(1, gangId.toString());
            statement.setString(2, systemKey);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new Conflict("Gang rank is missing: " + systemKey);
                return readRank(connection, row);
            }
        }
    }

    private static Gang readGang(ResultSet row) throws SQLException {
        Gang.GangHome home = null;
        String world = row.getString("home_world");
        if (world != null && !world.isBlank()) home = new Gang.GangHome(world, row.getDouble("home_x"),
                row.getDouble("home_y"), row.getDouble("home_z"), row.getFloat("home_yaw"), row.getFloat("home_pitch"));
        return new Gang(UUID.fromString(row.getString("gang_id")), row.getString("name"), row.getString("tag"),
                row.getString("description"), UUID.fromString(row.getString("owner_uuid")), row.getLong("created_at"),
                row.getInt("level"), row.getBigDecimal("xp"), row.getLong("points"),
                row.getBigDecimal("bank_balance"), row.getInt("member_limit"), row.getInt("member_count"),
                GangJoinMode.valueOf(row.getString("join_mode")), row.getString("color"), row.getString("motd"),
                home, row.getBoolean("enabled"), row.getLong("version"), row.getLong("updated_at"));
    }

    private static GangMember readMember(ResultSet row) throws SQLException {
        return new GangMember(UUID.fromString(row.getString("player_uuid")), UUID.fromString(row.getString("gang_id")),
                UUID.fromString(row.getString("rank_id")), row.getLong("joined_at"), row.getLong("last_seen_at"),
                row.getBoolean("chat_enabled"), row.getLong("version"));
    }

    private static GangRank readRank(Connection connection, ResultSet row) throws SQLException {
        UUID rankId = UUID.fromString(row.getString("rank_id"));
        EnumSet<GangPermission> permissions = EnumSet.noneOf(GangPermission.class);
        try (PreparedStatement statement = connection.prepareStatement("SELECT permission FROM rp_gang_rank_permissions "
                + "WHERE rank_id=? AND enabled=TRUE")) {
            statement.setString(1, rankId.toString());
            try (ResultSet permissionRows = statement.executeQuery()) {
                while (permissionRows.next()) permissions.add(GangPermission.parse(permissionRows.getString(1)));
            }
        }
        String systemKey = row.getString("system_key");
        if ("owner".equals(systemKey)) permissions.addAll(GangPermission.ownerPermissions());
        return new GangRank(rankId, UUID.fromString(row.getString("gang_id")), systemKey,
                row.getString("display_name"), row.getInt("priority"), row.getString("color"),
                row.getBoolean("is_default"), permissions, row.getLong("created_at"), row.getLong("updated_at"));
    }

    private static void insertRank(Connection connection, GangRank rank) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_ranks("
                + "rank_id,gang_id,system_key,display_name,priority,color,is_default,created_at,updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, rank.id().toString());
            statement.setString(2, rank.gangId().toString());
            if (rank.systemKey().isBlank()) statement.setNull(3, java.sql.Types.VARCHAR);
            else statement.setString(3, rank.systemKey());
            statement.setString(4, rank.displayName());
            statement.setInt(5, rank.priority());
            statement.setString(6, rank.color());
            statement.setBoolean(7, rank.defaultRank());
            statement.setLong(8, rank.createdAt());
            statement.setLong(9, rank.updatedAt());
            statement.executeUpdate();
        }
        for (GangPermission permission : rank.permissions()) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_rank_permissions("
                    + "rank_id,permission,enabled) VALUES(?,?,TRUE)")) {
                statement.setString(1, rank.id().toString());
                statement.setString(2, permission.key());
                statement.executeUpdate();
            }
        }
    }

    private static void insertMember(Connection connection, GangMember member) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_members("
                + "player_uuid,gang_id,rank_id,joined_at,last_seen_at,chat_enabled,version) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, member.playerId().toString());
            statement.setString(2, member.gangId().toString());
            statement.setString(3, member.rankId().toString());
            statement.setLong(4, member.joinedAt());
            statement.setLong(5, member.lastSeenAt());
            statement.setBoolean(6, member.chatEnabled());
            statement.setLong(7, member.version());
            statement.executeUpdate();
        }
    }

    private static void updateMemberRank(Connection connection, UUID playerId, UUID rankId, long version)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gang_members SET rank_id=?,"
                + "version=version+1 WHERE player_uuid=? AND version=?")) {
            statement.setString(1, rankId.toString());
            statement.setString(2, playerId.toString());
            statement.setLong(3, version);
            if (statement.executeUpdate() != 1) throw new Conflict("Member rank changed concurrently");
        }
    }

    private static Optional<GangInvite> invite(Connection connection, UUID inviteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_gang_invites WHERE invite_id=?")) {
            statement.setString(1, inviteId.toString());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readInvite(row)) : Optional.empty();
            }
        }
    }

    private static GangInvite readInvite(ResultSet row) throws SQLException {
        return new GangInvite(UUID.fromString(row.getString("invite_id")), UUID.fromString(row.getString("gang_id")),
                UUID.fromString(row.getString("player_uuid")), UUID.fromString(row.getString("invited_by")),
                row.getLong("created_at"), row.getLong("expires_at"),
                GangInvite.Status.valueOf(row.getString("status")));
    }

    private static void setInviteStatus(Connection connection, UUID inviteId, GangInvite.Status status)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rp_gang_invites SET status=? WHERE invite_id=?")) {
            statement.setString(1, status.name());
            statement.setString(2, inviteId.toString());
            statement.executeUpdate();
        }
    }

    private static void expireInvites(Connection connection, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gang_invites SET status='EXPIRED' "
                + "WHERE status='PENDING' AND expires_at<=?")) {
            statement.setLong(1, now);
            statement.executeUpdate();
        }
    }

    private static void revokeOtherInvites(Connection connection, UUID playerId, UUID acceptedInvite)
            throws SQLException {
        String sql = acceptedInvite == null
                ? "UPDATE rp_gang_invites SET status='REVOKED' WHERE player_uuid=? AND status='PENDING'"
                : "UPDATE rp_gang_invites SET status='REVOKED' WHERE player_uuid=? AND status='PENDING' AND invite_id<>?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            if (acceptedInvite != null) statement.setString(2, acceptedInvite.toString());
            statement.executeUpdate();
        }
    }

    private static Optional<GangBankTransaction> bankTransactionByOperation(Connection connection,
                                                                            String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_bank_transactions "
                + "WHERE operation_key=?")) {
            statement.setString(1, operationKey);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readBankTransaction(row)) : Optional.empty();
            }
        }
    }

    private static GangBankTransaction readBankTransaction(ResultSet row) throws SQLException {
        String player = row.getString("player_uuid");
        return new GangBankTransaction(UUID.fromString(row.getString("transaction_id")),
                UUID.fromString(row.getString("gang_id")), player == null ? null : UUID.fromString(player),
                row.getBigDecimal("amount"), row.getBigDecimal("previous_balance"), row.getBigDecimal("new_balance"),
                GangBankTransaction.Type.valueOf(row.getString("transaction_type")), row.getString("reason"),
                row.getLong("created_at"), row.getString("operation_key"));
    }

    private static void insertBankTransaction(Connection connection, GangBankTransaction transaction)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_bank_transactions("
                + "transaction_id,gang_id,player_uuid,amount,previous_balance,new_balance,transaction_type,reason,"
                + "created_at,operation_key) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, transaction.id().toString());
            statement.setString(2, transaction.gangId().toString());
            nullableUuid(statement, 3, transaction.playerId());
            statement.setBigDecimal(4, transaction.amount());
            statement.setBigDecimal(5, transaction.previousBalance());
            statement.setBigDecimal(6, transaction.newBalance());
            statement.setString(7, transaction.type().name());
            statement.setString(8, safe(transaction.reason(), 256));
            statement.setLong(9, transaction.createdAt());
            statement.setString(10, transaction.operationKey());
            statement.executeUpdate();
        }
    }

    private static BigDecimal withdrawalUsage(Connection connection, UUID gangId, UUID playerId, String periodKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT amount FROM rp_gang_withdrawal_usage "
                + "WHERE gang_id=? AND player_uuid=? AND period_key=?")) {
            statement.setString(1, gangId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, periodKey);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private static void setWithdrawalUsage(Connection connection, UUID gangId, UUID playerId, String periodKey,
                                           BigDecimal amount) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE rp_gang_withdrawal_usage SET amount=? "
                + "WHERE gang_id=? AND player_uuid=? AND period_key=?")) {
            update.setBigDecimal(1, amount);
            update.setString(2, gangId.toString());
            update.setString(3, playerId.toString());
            update.setString(4, periodKey);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO rp_gang_withdrawal_usage("
                        + "gang_id,player_uuid,period_key,amount) VALUES(?,?,?,?)")) {
                    insert.setString(1, gangId.toString());
                    insert.setString(2, playerId.toString());
                    insert.setString(3, periodKey);
                    insert.setBigDecimal(4, amount);
                    insert.executeUpdate();
                }
            }
        }
    }

    private static int upgradeTier(Connection connection, UUID gangId, String upgradeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT tier FROM rp_gang_upgrades "
                + "WHERE gang_id=? AND upgrade_id=?")) {
            statement.setString(1, gangId.toString());
            statement.setString(2, upgradeId);
            try (ResultSet row = statement.executeQuery()) { return row.next() ? row.getInt(1) : 0; }
        }
    }

    private static void upsertUpgrade(Connection connection, UUID gangId, String upgradeId, int tier,
                                      UUID actorId, long now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE rp_gang_upgrades SET tier=?,"
                + "updated_at=?,updated_by=? WHERE gang_id=? AND upgrade_id=?")) {
            update.setInt(1, tier);
            update.setLong(2, now);
            nullableUuid(update, 3, actorId);
            update.setString(4, gangId.toString());
            update.setString(5, upgradeId);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO rp_gang_upgrades("
                        + "gang_id,upgrade_id,tier,updated_at,updated_by) VALUES(?,?,?,?,?)")) {
                    insert.setString(1, gangId.toString());
                    insert.setString(2, upgradeId);
                    insert.setInt(3, tier);
                    insert.setLong(4, now);
                    nullableUuid(insert, 5, actorId);
                    insert.executeUpdate();
                }
            }
        }
    }

    private static void ensureMemberStatistics(Connection connection, UUID gangId, UUID playerId, long now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM rp_gang_member_statistics "
                + "WHERE gang_id=? AND player_uuid=?")) {
            statement.setString(1, gangId.toString());
            statement.setString(2, playerId.toString());
            try (ResultSet row = statement.executeQuery()) { if (row.next()) return; }
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_member_statistics("
                + "gang_id,player_uuid,blocks_mined,money_earned,gang_xp_earned,rankups,prestiges,block_events,updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, gangId.toString());
            statement.setString(2, playerId.toString());
            statement.setLong(3, 0L);
            statement.setBigDecimal(4, BigDecimal.ZERO);
            statement.setBigDecimal(5, BigDecimal.ZERO);
            statement.setLong(6, 0L);
            statement.setLong(7, 0L);
            statement.setLong(8, 0L);
            statement.setLong(9, now);
            statement.executeUpdate();
        }
    }

    private static void updateStatistics(Connection connection, String table, String whereColumn, String whereValue,
                                         ContributionColumns columns, BigDecimal xp, long now) throws SQLException {
        updateStatistics(connection, table, whereColumn + "=?", List.of(whereValue), columns, xp, now);
    }

    private static void updateStatistics(Connection connection, String table, String where,
                                         List<String> whereValues, ContributionColumns columns,
                                         BigDecimal xp, long now) throws SQLException {
        String sql = "UPDATE " + table + " SET blocks_mined=blocks_mined+?,money_earned=money_earned+?,"
                + "gang_xp_earned=gang_xp_earned+?,rankups=rankups+?,prestiges=prestiges+?,"
                + "block_events=block_events+?,updated_at=? WHERE " + where;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, columns.blocks());
            statement.setBigDecimal(2, columns.money());
            statement.setBigDecimal(3, xp);
            statement.setLong(4, columns.rankups());
            statement.setLong(5, columns.prestiges());
            statement.setLong(6, columns.blockEvents());
            statement.setLong(7, now);
            int index = 8;
            for (String value : whereValues) statement.setString(index++, value);
            if (statement.executeUpdate() != 1) throw new Conflict("Gang statistics row is unavailable");
        }
    }

    private static ProgressionState applyXp(Connection connection, Gang gang, BigDecimal addedXp,
                                            GangConfig config, long now) throws SQLException {
        int oldLevel = gang.level();
        int level = oldLevel;
        BigDecimal xp = gang.xp().add(addedXp);
        while (level < config.maximumLevel()) {
            BigDecimal required = config.requiredXp(level);
            if (required.signum() <= 0 || xp.compareTo(required) < 0) break;
            xp = xp.subtract(required);
            level++;
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gangs SET level=?,xp=?,"
                + "version=version+1,updated_at=? WHERE gang_id=? AND version=? AND enabled=TRUE")) {
            statement.setInt(1, level);
            statement.setBigDecimal(2, xp);
            statement.setLong(3, now);
            statement.setString(4, gang.id().toString());
            statement.setLong(5, gang.version());
            if (statement.executeUpdate() != 1) throw new Conflict("Gang progression changed concurrently");
        }
        return new ProgressionState(oldLevel, level, xp);
    }

    private static List<String> applyMissionProgress(Connection connection, UUID gangId, UUID playerId,
                                                      GangConfig.ContributionType type, String targetKey,
                                                      BigDecimal amount, GangConfig config, String periodKey,
                                                      long now) throws SQLException {
        List<String> completed = new ArrayList<>();
        for (GangConfig.MissionDefinition definition : config.missions().values()) {
            if (!missionMatches(definition, type, targetKey)) continue;
            String effectivePeriod = missionPeriod(connection, definition, periodKey, now);
            String instanceId = missionInstanceId(gangId, definition.id(), effectivePeriod);
            GangMissionState state = mission(connection, instanceId).orElse(null);
            if (state == null) {
                state = new GangMissionState(instanceId, gangId, definition.id(), effectivePeriod, BigDecimal.ZERO,
                        definition.target(), GangMissionState.State.ACTIVE, null, null, now);
                insertMission(connection, state);
            }
            if (state.state() != GangMissionState.State.ACTIVE) continue;
            BigDecimal progress = state.progress().add(amount).min(state.target());
            boolean done = progress.compareTo(state.target()) >= 0;
            try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gang_missions SET progress=?,"
                    + "state=?,completed_at=?,updated_at=? WHERE mission_instance_id=? AND state='ACTIVE'")) {
                statement.setBigDecimal(1, progress);
                statement.setString(2, done ? GangMissionState.State.COMPLETED.name() : GangMissionState.State.ACTIVE.name());
                if (done) statement.setLong(3, now); else statement.setNull(3, java.sql.Types.BIGINT);
                statement.setLong(4, now);
                statement.setString(5, instanceId);
                statement.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE rp_gang_mission_progress SET "
                    + "progress=progress+?,updated_at=? WHERE mission_instance_id=? AND player_uuid=?")) {
                update.setBigDecimal(1, amount);
                update.setLong(2, now);
                update.setString(3, instanceId);
                update.setString(4, playerId.toString());
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO rp_gang_mission_progress("
                            + "mission_instance_id,player_uuid,progress,updated_at) VALUES(?,?,?,?)")) {
                        insert.setString(1, instanceId);
                        insert.setString(2, playerId.toString());
                        insert.setBigDecimal(3, amount);
                        insert.setLong(4, now);
                        insert.executeUpdate();
                    }
                }
            }
            if (done) completed.add(definition.id());
        }
        return List.copyOf(completed);
    }

    private static boolean missionMatches(GangConfig.MissionDefinition definition,
                                          GangConfig.ContributionType type, String targetKey) {
        return switch (definition.objective()) {
            case BLOCKS -> type == GangConfig.ContributionType.BLOCKS;
            case MATERIAL_BLOCKS -> type == GangConfig.ContributionType.BLOCKS
                    && definition.targetKey().equalsIgnoreCase(targetKey);
            case MONEY -> type == GangConfig.ContributionType.MONEY;
            case BLOCK_EVENTS -> type == GangConfig.ContributionType.BLOCK_EVENTS;
            case RANKUPS -> type == GangConfig.ContributionType.RANKUPS;
            case PRESTIGES -> type == GangConfig.ContributionType.PRESTIGES;
        };
    }

    private static List<GangLeaderboardEntry> leaderboard(Connection connection, String category, int limit)
            throws SQLException {
        return leaderboard(connection, category, limit, null, null);
    }

    private static List<GangLeaderboardEntry> leaderboard(Connection connection, String category, int limit,
                                                           String periodType, String periodKey) throws SQLException {
        boolean period = periodType != null;
        String expression = switch (category.toLowerCase(Locale.ROOT)) {
            case "level" -> "g.level";
            case "xp" -> period ? "s.gang_xp_earned" : "g.xp";
            case "blocks" -> "s.blocks_mined";
            case "money" -> "s.money_earned";
            case "prestiges" -> "s.prestiges";
            case "balance" -> "g.bank_balance";
            case "block_events" -> "s.block_events";
            default -> throw new IllegalArgumentException("Unknown gang leaderboard category: " + category);
        };
        String statisticsTable = period ? "rp_gang_period_statistics" : "rp_gang_statistics";
        String sql = "SELECT g.gang_id,g.name," + expression + " AS value FROM rp_gangs g "
                + "JOIN " + statisticsTable + " s ON s.gang_id=g.gang_id WHERE g.enabled=TRUE"
                + (period ? " AND s.period_type=? AND s.period_key=?" : "") + " ORDER BY value DESC,"
                + "LOWER(g.name),g.gang_id LIMIT ?";
        List<GangLeaderboardEntry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (period) {
                statement.setString(index++, periodType);
                statement.setString(index++, periodKey);
            }
            statement.setInt(index, Math.max(1, Math.min(1000, limit)));
            try (ResultSet rows = statement.executeQuery()) {
                int position = 1;
                while (rows.next()) result.add(new GangLeaderboardEntry(position++,
                        UUID.fromString(rows.getString("gang_id")), rows.getString("name"),
                        rows.getBigDecimal("value")));
            }
        }
        return List.copyOf(result);
    }

    private static void updatePeriodStatistics(Connection connection, UUID gangId, ContributionColumns columns,
                                               BigDecimal xp, String composite, long now) throws SQLException {
        String[] values = composite.split("\\|", -1);
        List<Period> periods = new ArrayList<>();
        if (values.length > 0 && !values[0].isBlank()) periods.add(new Period("daily", values[0]));
        if (values.length > 1 && !values[1].isBlank()) periods.add(new Period("weekly", values[1]));
        if (values.length > 2 && !values[2].isBlank()) periods.add(new Period("monthly", values[2]));
        try (PreparedStatement statement = connection.prepareStatement("SELECT season_id FROM rp_gang_seasons "
                + "WHERE starts_at<=? AND ends_at>? AND state IN ('SCHEDULED','ACTIVE')")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) periods.add(new Period("season", rows.getString(1)));
            }
        }
        for (Period period : periods) {
            ensurePeriodStatistics(connection, gangId, period, now);
            updateStatistics(connection, "rp_gang_period_statistics",
                    "gang_id=? AND period_type=? AND period_key=?",
                    List.of(gangId.toString(), period.type(), period.key()), columns, xp, now);
        }
    }

    private static void ensurePeriodStatistics(Connection connection, UUID gangId, Period period, long now)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM rp_gang_period_statistics "
                + "WHERE gang_id=? AND period_type=? AND period_key=?")) {
            select.setString(1, gangId.toString());
            select.setString(2, period.type());
            select.setString(3, period.key());
            try (ResultSet row = select.executeQuery()) { if (row.next()) return; }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO rp_gang_period_statistics("
                + "gang_id,period_type,period_key,blocks_mined,money_earned,gang_xp_earned,rankups,prestiges,"
                + "block_events,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, gangId.toString());
            insert.setString(2, period.type());
            insert.setString(3, period.key());
            insert.setLong(4, 0L);
            insert.setBigDecimal(5, BigDecimal.ZERO);
            insert.setBigDecimal(6, BigDecimal.ZERO);
            insert.setLong(7, 0L);
            insert.setLong(8, 0L);
            insert.setLong(9, 0L);
            insert.setLong(10, now);
            insert.executeUpdate();
        }
    }

    private static Optional<GangSeason> season(Connection connection, String seasonId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_seasons WHERE season_id=?")) {
            statement.setString(1, seasonId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                long finalized = row.getLong("finalized_at");
                Long finalizedAt = row.wasNull() ? null : finalized;
                String categories = row.getString("tracked_categories");
                return Optional.of(new GangSeason(seasonId, row.getLong("starts_at"), row.getLong("ends_at"),
                        categories.isBlank() ? List.of() : List.of(categories.split(",")), row.getString("reward_plan"),
                        GangSeason.State.valueOf(row.getString("state")), row.getLong("created_at"), finalizedAt));
            }
        }
    }

    private static void insertSeasonResult(Connection connection, GangSeasonResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_season_results("
                + "season_id,category,position,gang_id,gang_name,value,frozen_reward,reward_state,recorded_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, result.seasonId());
            statement.setString(2, result.category());
            statement.setInt(3, result.position());
            statement.setString(4, result.gangId().toString());
            statement.setString(5, result.gangName());
            statement.setBigDecimal(6, result.value());
            statement.setString(7, result.frozenReward());
            statement.setString(8, result.rewardState());
            statement.setLong(9, result.recordedAt());
            statement.executeUpdate();
        }
    }

    private static List<GangSeasonResult> seasonResults(Connection connection, String seasonId) throws SQLException {
        List<GangSeasonResult> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_gang_season_results "
                + "WHERE season_id=? ORDER BY category,position")) {
            statement.setString(1, seasonId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new GangSeasonResult(seasonId, rows.getString("category"),
                        rows.getInt("position"), UUID.fromString(rows.getString("gang_id")),
                        rows.getString("gang_name"), rows.getBigDecimal("value"), rows.getString("frozen_reward"),
                        rows.getString("reward_state"), rows.getLong("recorded_at")));
            }
        }
        return List.copyOf(result);
    }

    private static Optional<GangMissionState> mission(Connection connection, String instanceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM rp_gang_missions WHERE mission_instance_id=?")) {
            statement.setString(1, instanceId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readMission(row)) : Optional.empty();
            }
        }
    }

    private static void insertMission(Connection connection, GangMissionState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_missions("
                + "mission_instance_id,gang_id,mission_id,period_key,progress,target,state,completed_at,claimed_at,updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, state.instanceId());
            statement.setString(2, state.gangId().toString());
            statement.setString(3, state.missionId());
            statement.setString(4, state.periodKey());
            statement.setBigDecimal(5, state.progress());
            statement.setBigDecimal(6, state.target());
            statement.setString(7, state.state().name());
            nullableLong(statement, 8, state.completedAt());
            nullableLong(statement, 9, state.claimedAt());
            statement.setLong(10, state.updatedAt());
            statement.executeUpdate();
        }
    }

    private static GangMissionState readMission(ResultSet row) throws SQLException {
        long completed = row.getLong("completed_at");
        Long completedAt = row.wasNull() ? null : completed;
        long claimed = row.getLong("claimed_at");
        Long claimedAt = row.wasNull() ? null : claimed;
        return new GangMissionState(row.getString("mission_instance_id"),
                UUID.fromString(row.getString("gang_id")), row.getString("mission_id"), row.getString("period_key"),
                row.getBigDecimal("progress"), row.getBigDecimal("target"),
                GangMissionState.State.valueOf(row.getString("state")), completedAt, claimedAt,
                row.getLong("updated_at"));
    }

    private static void applyMissionReward(Connection connection, UUID gangId, UUID actorId, String instanceId,
                                           GangConfig.MissionReward reward, GangConfig config, long now)
            throws SQLException {
        Gang gang = enabledGang(connection, gangId);
        BigDecimal balance = gang.bankBalance().add(reward.gangMoney());
        try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gangs SET points=points+?,"
                + "bank_balance=?,version=version+1,updated_at=? WHERE gang_id=? AND version=?")) {
            statement.setLong(1, reward.gangPoints());
            statement.setBigDecimal(2, balance);
            statement.setLong(3, now);
            statement.setString(4, gangId.toString());
            statement.setLong(5, gang.version());
            if (statement.executeUpdate() != 1) throw new Conflict("Gang rewards changed concurrently");
        }
        if (reward.gangMoney().signum() != 0) {
            insertBankTransaction(connection, new GangBankTransaction(UUID.randomUUID(), gangId, actorId,
                    reward.gangMoney(), gang.bankBalance(), balance, GangBankTransaction.Type.MISSION_REWARD,
                    instanceId, now, "mission:" + instanceId));
        }
        if (reward.gangXp().signum() > 0) {
            Gang refreshed = gangById(connection, gangId).orElseThrow();
            applyXp(connection, refreshed, reward.gangXp(), config, now);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE rp_gang_statistics SET "
                    + "gang_xp_earned=gang_xp_earned+?,updated_at=? WHERE gang_id=?")) {
                statement.setBigDecimal(1, reward.gangXp());
                statement.setLong(2, now);
                statement.setString(3, gangId.toString());
                statement.executeUpdate();
            }
        }
        if (reward.boosterType() != null && reward.boosterDuration().toMillis() > 0) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_boosters("
                    + "booster_id,gang_id,booster_type,multiplier,starts_at,expires_at,activated_by,enabled,source,created_at) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, gangId.toString());
                statement.setString(3, reward.boosterType().name());
                statement.setBigDecimal(4, reward.boosterMultiplier());
                statement.setLong(5, now);
                statement.setLong(6, now + reward.boosterDuration().toMillis());
                nullableUuid(statement, 7, actorId);
                statement.setBoolean(8, true);
                statement.setString(9, "mission:" + instanceId);
                statement.setLong(10, now);
                statement.executeUpdate();
            }
        }
        if (!reward.rewardComponents().isEmpty()) {
            insertOperationClaim(connection, "gang-mission-reward:" + instanceId, gangId, "mission_reward",
                    (actorId == null ? "" : actorId.toString()) + "|" + String.join(";", reward.rewardComponents()), now);
        }
    }

    private static GangStatistics readStatistics(ResultSet row) throws SQLException {
        return new GangStatistics(UUID.fromString(row.getString("gang_id")), row.getLong("blocks_mined"),
                row.getBigDecimal("money_earned"), row.getBigDecimal("gang_xp_earned"), row.getLong("rankups"),
                row.getLong("prestiges"), row.getLong("block_events"), row.getLong("updated_at"));
    }

    private static GangMemberStatistics readMemberStatistics(ResultSet row) throws SQLException {
        return new GangMemberStatistics(UUID.fromString(row.getString("gang_id")),
                UUID.fromString(row.getString("player_uuid")), row.getLong("blocks_mined"),
                row.getBigDecimal("money_earned"), row.getBigDecimal("gang_xp_earned"), row.getLong("rankups"),
                row.getLong("prestiges"), row.getLong("block_events"), row.getLong("updated_at"));
    }

    private static GangBooster readBooster(ResultSet row) throws SQLException {
        String actor = row.getString("activated_by");
        return new GangBooster(UUID.fromString(row.getString("booster_id")),
                UUID.fromString(row.getString("gang_id")), GangBooster.Type.valueOf(row.getString("booster_type")),
                row.getBigDecimal("multiplier"), row.getLong("starts_at"), row.getLong("expires_at"),
                actor == null ? null : UUID.fromString(actor), row.getBoolean("enabled"), row.getString("source"),
                row.getLong("created_at"));
    }

    private static GangAuditEntry readAudit(ResultSet row) throws SQLException {
        String actor = row.getString("actor_uuid");
        return new GangAuditEntry(UUID.fromString(row.getString("audit_id")),
                UUID.fromString(row.getString("gang_id")), actor == null ? null : UUID.fromString(actor),
                row.getString("action_type"), row.getString("target_type"), row.getString("target_id"),
                row.getString("before_summary"), row.getString("after_summary"), row.getBoolean("success"),
                row.getString("reason"), row.getString("metadata"), row.getLong("created_at"));
    }

    private static void audit(Connection connection, UUID gangId, UUID actorId, String actionType,
                              String targetType, String targetId, String before, String after, boolean success,
                              String reason, String metadata, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_audit("
                + "audit_id,gang_id,actor_uuid,action_type,target_type,target_id,before_summary,after_summary,"
                + "success,reason,metadata,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, gangId.toString());
            nullableUuid(statement, 3, actorId);
            statement.setString(4, safe(actionType, 64));
            statement.setString(5, safe(targetType, 32));
            statement.setString(6, safe(targetId, 128));
            statement.setString(7, safe(before, 2048));
            statement.setString(8, safe(after, 2048));
            statement.setBoolean(9, success);
            statement.setString(10, safe(reason, 512));
            statement.setString(11, safe(metadata, 2048));
            statement.setLong(12, now);
            statement.executeUpdate();
        }
    }

    private static Optional<String> operationClaim(Connection connection, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT result_payload FROM "
                + "rp_gang_operation_claims WHERE operation_key=?")) {
            statement.setString(1, operationKey);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(row.getString(1)) : Optional.empty();
            }
        }
    }

    private static void insertOperationClaim(Connection connection, String operationKey, UUID gangId,
                                             String type, String result, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_gang_operation_claims("
                + "operation_key,gang_id,operation_type,result_payload,created_at) VALUES(?,?,?,?,?)")) {
            statement.setString(1, operationKey);
            nullableUuid(statement, 2, gangId);
            statement.setString(3, type);
            statement.setString(4, result);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    private static String missionInstanceId(UUID gangId, String missionId, String periodKey) {
        return gangId + ":" + missionId + ":" + periodKey;
    }

    private static String missionPeriod(Connection connection, GangConfig.MissionDefinition definition,
                                        String composite, long now) throws SQLException {
        String[] values = composite.split("\\|", -1);
        return switch (definition.resetPeriod()) {
            case DAILY -> values.length > 0 ? values[0] : composite;
            case WEEKLY -> values.length > 1 ? values[1] : composite;
            case MONTHLY -> values.length > 2 ? values[2] : composite;
            case SEASON -> activeSeasonId(connection, now).orElse("no-season");
        };
    }

    private static Optional<String> activeSeasonId(Connection connection, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT season_id FROM rp_gang_seasons "
                + "WHERE starts_at<=? AND ends_at>? AND state IN ('SCHEDULED','ACTIVE') "
                + "ORDER BY starts_at DESC,season_id LIMIT 1")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(row.getString(1)) : Optional.empty();
            }
        }
    }

    private static void execute(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    private static void nullableUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value.toString());
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean constraint(SQLException error) {
        String state = error.getSQLState();
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        return (state != null && state.startsWith("23")) || message.contains("constraint")
                || message.contains("duplicate") || message.contains("unique");
    }

    private static GangOperationResult operationFailure(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return GangOperationResult.failure(root.getMessage() == null ? "Gang operation failed" : root.getMessage());
    }

    private static <T> T transaction(Connection connection, SqlWork<T> work) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        int previousIsolation = connection.getTransactionIsolation();
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
        try {
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (Throwable error) {
            connection.rollback();
            if (error instanceof Exception exception) throw exception;
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
            connection.setTransactionIsolation(previousIsolation);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T apply(Connection connection) throws Exception; }

    private record ProgressionState(int oldLevel, int newLevel, BigDecimal remainingXp) { }
    private record Period(String type, String key) { }

    private record ContributionColumns(long blocks, BigDecimal money, long rankups, long prestiges,
                                       long blockEvents) {
        static ContributionColumns forType(GangConfig.ContributionType type, BigDecimal amount) {
            return switch (type) {
                case BLOCKS -> new ContributionColumns(amount.longValue(), BigDecimal.ZERO, 0L, 0L, 0L);
                case MONEY -> new ContributionColumns(0L, amount, 0L, 0L, 0L);
                case RANKUPS -> new ContributionColumns(0L, BigDecimal.ZERO, amount.longValue(), 0L, 0L);
                case PRESTIGES -> new ContributionColumns(0L, BigDecimal.ZERO, 0L, amount.longValue(), 0L);
                case BLOCK_EVENTS -> new ContributionColumns(0L, BigDecimal.ZERO, 0L, 0L, amount.longValue());
                case MISSIONS, LEADERBOARD -> new ContributionColumns(0L, BigDecimal.ZERO, 0L, 0L, 0L);
            };
        }
    }

    public static final class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public Conflict(String message) { super(message); }
        public Conflict(String message, Throwable cause) { super(message, cause); }
    }

    public record MissionRewardClaim(String operationKey, UUID gangId, String payload) { }
}
