package site.mcrelicworld.relicprison.database;

import site.mcrelicworld.relicprison.config.StorageConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class SimpleConnectionPool implements AutoCloseable {
    private final StorageConfig config;
    private final ArrayBlockingQueue<Connection> idle;
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int maxSize;

    SimpleConnectionPool(StorageConfig config) {
        this.config = config;
        this.maxSize = config.type() == StorageConfig.Type.SQLITE ? 1 : Math.max(1, config.poolSize());
        this.idle = new ArrayBlockingQueue<>(maxSize);
    }

    Connection borrow() throws SQLException {
        if (closed.get()) throw new SQLException("Connection pool is closed");
        Connection existing = idle.poll();
        if (isUsable(existing)) return existing;
        if (existing != null) closeQuietly(existing);

        while (true) {
            int count = created.get();
            if (count < maxSize && created.compareAndSet(count, count + 1)) {
                try { return open(); }
                catch (SQLException ex) { created.decrementAndGet(); throw ex; }
            }
            try {
                Connection waited = idle.poll(10, TimeUnit.SECONDS);
                if (isUsable(waited)) return waited;
                if (waited != null) { closeQuietly(waited); created.decrementAndGet(); }
                if (closed.get()) throw new SQLException("Connection pool closed while waiting");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for database connection", ex);
            }
        }
    }

    void release(Connection connection) {
        if (connection == null) return;
        if (closed.get() || !isUsable(connection) || !idle.offer(connection)) {
            closeQuietly(connection);
            created.decrementAndGet();
        }
    }

    DatabaseManager.PoolSnapshot snapshot() {
        return new DatabaseManager.PoolSnapshot(true, created.get(), idle.size(), maxSize, closed.get());
    }

    private Connection open() throws SQLException {
        if (config.type() == StorageConfig.Type.SQLITE) {
            return DriverManager.getConnection("jdbc:sqlite:" + config.sqliteFile().toAbsolutePath());
        }
        String url = "jdbc:mysql://" + config.host() + ':' + config.port() + '/' + config.database()
                + (config.parameters().isBlank() ? "" : "?" + config.parameters());
        return DriverManager.getConnection(url, config.username(), config.password());
    }

    private static boolean isUsable(Connection connection) {
        if (connection == null) return false;
        try { return !connection.isClosed() && connection.isValid(2); }
        catch (SQLException ex) { return false; }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<Connection> connections = new ArrayList<>();
        idle.drainTo(connections);
        for (Connection connection : connections) closeQuietly(connection);
        created.set(0);
    }

    private static void closeQuietly(Connection connection) {
        try { connection.close(); } catch (SQLException ignored) {}
    }
}
