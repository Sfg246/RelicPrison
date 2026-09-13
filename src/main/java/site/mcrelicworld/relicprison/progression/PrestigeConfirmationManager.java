package site.mcrelicworld.relicprison.progression;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrestigeConfirmationManager {
    private final Map<UUID, Long> confirmations = new ConcurrentHashMap<>();

    public void request(UUID playerId, int seconds) {
        confirmations.put(playerId, System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean consume(UUID playerId) {
        Long expiry = confirmations.remove(playerId);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    public void clear(UUID playerId) { confirmations.remove(playerId); }
}
