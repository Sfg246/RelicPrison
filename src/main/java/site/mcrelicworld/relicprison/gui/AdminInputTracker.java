package site.mcrelicworld.relicprison.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent, single-consumption input ownership used by the live admin chat flow. */
public final class AdminInputTracker<T> {
    private final Map<UUID, TimedInput<T>> inputs = new ConcurrentHashMap<>();

    public void begin(UUID playerId, T value, long expiresAt) {
        inputs.put(playerId, new TimedInput<>(value, expiresAt));
    }

    public Optional<T> pending(UUID playerId, long now) {
        TimedInput<T> input = inputs.get(playerId);
        if (input == null) return Optional.empty();
        if (now <= input.expiresAt()) return Optional.of(input.value());
        inputs.remove(playerId, input);
        return Optional.empty();
    }

    public Optional<T> consume(UUID playerId, long now) {
        TimedInput<T> input = inputs.remove(playerId);
        return input == null || now > input.expiresAt() ? Optional.empty() : Optional.of(input.value());
    }

    public boolean expire(UUID playerId, T expected, long now) {
        TimedInput<T> input = inputs.get(playerId);
        return input != null && input.value() == expected && now > input.expiresAt()
                && inputs.remove(playerId, input);
    }

    public void clear(UUID playerId) { inputs.remove(playerId); }
    public void clear() { inputs.clear(); }
    public int size() { return inputs.size(); }

    private record TimedInput<T>(T value, long expiresAt) { }
}
