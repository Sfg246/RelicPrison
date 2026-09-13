package site.mcrelicworld.relicprison.reset;

import site.mcrelicworld.relicprison.api.model.MineResetState;
import site.mcrelicworld.relicprison.config.StorageConfig;
import site.mcrelicworld.relicprison.database.DatabaseManager;
import site.mcrelicworld.relicprison.mine.MineDefinition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class MineRuntimeRepository {
    private final DatabaseManager database;

    public MineRuntimeRepository(DatabaseManager database) { this.database = database; }

    public CompletableFuture<Map<String, MineRuntime>> load(Collection<MineDefinition> mines) {
        return database.submitIdempotent(connection -> {
            Map<String, MineRuntime> loaded = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_mine_runtime");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String id = result.getString("mine_id");
                    MineResetState.State state;
                    try { state = MineResetState.State.valueOf(result.getString("state")); }
                    catch (RuntimeException ex) { state = MineResetState.State.IDLE; }
                    loaded.put(id, new MineRuntime(id, result.getLong("remaining_blocks"),
                            result.getLong("reset_count"), result.getLong("last_reset"), result.getLong("next_reset"),
                            result.getLong("last_reset_duration_ms"), state, result.getLong("last_recount_at"),
                            result.getString("last_recount_reason"), result.getString("last_recount_actor")));
                }
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
            long now = System.currentTimeMillis();
            for (MineDefinition mine : mines) {
                MineRuntime runtime = loaded.get(mine.id());
                if (runtime == null) {
                    runtime = new MineRuntime(mine.id(), mine.volume(), 0, 0,
                            now + mine.resetConfig().intervalSeconds() * 1000L, 0, MineResetState.State.IDLE);
                    runtime.markDirty();
                    loaded.put(mine.id(), runtime);
                }
                runtime.reconcileVolume(mine.volume());
            }
            return loaded;
        });
    }

    public CompletableFuture<Void> save(MineRuntime runtime) {
        return database.submitIdempotent(connection -> {
            long revision = runtime.revision();
            String sql = "INSERT INTO rp_mine_runtime (mine_id,remaining_blocks,reset_count,last_reset,next_reset," +
                    "last_reset_duration_ms,state,last_recount_at,last_recount_reason,last_recount_actor) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?) " + upsertClause();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, runtime.mineId());
                statement.setLong(2, runtime.remainingBlocks());
                statement.setLong(3, runtime.resetCount());
                statement.setLong(4, runtime.lastReset());
                statement.setLong(5, runtime.nextReset());
                statement.setLong(6, runtime.lastResetDurationMillis());
                statement.setString(7, runtime.state().name());
                statement.setLong(8, runtime.lastRecountAt());
                statement.setString(9, runtime.lastRecountReason());
                statement.setString(10, runtime.lastRecountActor());
                statement.executeUpdate();
                runtime.markSaved(revision);
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Void> saveDirty(Collection<MineRuntime> runtimes) {
        CompletableFuture<?>[] futures = runtimes.stream().filter(MineRuntime::dirty).map(this::save)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<Void> delete(String mineId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM rp_mine_runtime WHERE mine_id=?")) {
                statement.setString(1, mineId);
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Void> saveFailure(ResetFailureRecord failure) {
        return database.submitIdempotent(connection -> {
            String sql = "INSERT INTO rp_reset_failures (reset_id,mine_id,reason,stage,started_at,failed_at,duration_ms," +
                    "total_blocks,processed_blocks,vanilla_blocks,itemsadder_blocks,air_blocks,reset_count_at_failure," +
                    "error_summary,retry_eligible) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " + failureUpsertClause();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, failure.resetId());
                statement.setString(2, failure.mineId());
                statement.setString(3, failure.reason());
                statement.setString(4, failure.stage());
                statement.setLong(5, failure.startedAt());
                statement.setLong(6, failure.failedAt());
                statement.setLong(7, failure.durationMillis());
                statement.setLong(8, failure.totalBlocks());
                statement.setLong(9, failure.processedBlocks());
                statement.setLong(10, failure.vanillaBlocks());
                statement.setLong(11, failure.itemsAdderBlocks());
                statement.setLong(12, failure.airBlocks());
                statement.setLong(13, failure.resetCountAtFailure());
                statement.setString(14, failure.errorSummary());
                statement.setBoolean(15, failure.retryEligible());
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Optional<ResetFailureRecord>> latestRetryableFailure(String mineId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rp_reset_failures WHERE mine_id=? AND retry_eligible=1 ORDER BY failed_at DESC")) {
                statement.setString(1, mineId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();
                    return Optional.of(failure(result));
                }
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    public CompletableFuture<Void> markFailureNotRetryable(String resetId) {
        return database.submitIdempotent(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE rp_reset_failures SET retry_eligible=0 WHERE reset_id=?")) {
                statement.setString(1, resetId);
                statement.executeUpdate();
                return null;
            } catch (SQLException ex) {
                throw new DatabaseManager.DatabaseException(ex);
            }
        });
    }

    private String upsertClause() {
        if (database.storageType() == StorageConfig.Type.MYSQL) {
            return "ON DUPLICATE KEY UPDATE remaining_blocks=VALUES(remaining_blocks),reset_count=VALUES(reset_count)," +
                    "last_reset=VALUES(last_reset),next_reset=VALUES(next_reset)," +
                    "last_reset_duration_ms=VALUES(last_reset_duration_ms),state=VALUES(state)," +
                    "last_recount_at=VALUES(last_recount_at),last_recount_reason=VALUES(last_recount_reason)," +
                    "last_recount_actor=VALUES(last_recount_actor)";
        }
        return "ON CONFLICT(mine_id) DO UPDATE SET remaining_blocks=excluded.remaining_blocks," +
                "reset_count=excluded.reset_count,last_reset=excluded.last_reset,next_reset=excluded.next_reset," +
                "last_reset_duration_ms=excluded.last_reset_duration_ms,state=excluded.state," +
                "last_recount_at=excluded.last_recount_at,last_recount_reason=excluded.last_recount_reason," +
                "last_recount_actor=excluded.last_recount_actor";
    }

    private String failureUpsertClause() {
        if (database.storageType() == StorageConfig.Type.MYSQL) {
            return "ON DUPLICATE KEY UPDATE retry_eligible=VALUES(retry_eligible),error_summary=VALUES(error_summary)";
        }
        return "ON CONFLICT(reset_id) DO UPDATE SET retry_eligible=excluded.retry_eligible," +
                "error_summary=excluded.error_summary";
    }

    private static ResetFailureRecord failure(ResultSet result) throws SQLException {
        return new ResetFailureRecord(result.getString("reset_id"), result.getString("mine_id"),
                result.getString("reason"), result.getString("stage"), result.getLong("started_at"),
                result.getLong("failed_at"), result.getLong("duration_ms"), result.getLong("total_blocks"),
                result.getLong("processed_blocks"), result.getLong("vanilla_blocks"),
                result.getLong("itemsadder_blocks"), result.getLong("air_blocks"),
                result.getLong("reset_count_at_failure"), result.getString("error_summary"),
                result.getBoolean("retry_eligible"));
    }
}
