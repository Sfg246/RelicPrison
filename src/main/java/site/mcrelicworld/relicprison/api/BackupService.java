package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.BackupView;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** Stable contract for later compressed backup and restore operations. */
public interface BackupService {
    /** Asynchronous. Returns a future and must not be joined on the main thread. */
    CompletableFuture<BackupView> create(String type);
    /** Thread-safe. Returns immutable known backup metadata. */
    Collection<BackupView> backups();
}
