package site.mcrelicworld.relicprison.admin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Runs runtime reload work on the server thread while file I/O remains off-thread. */
public final class ServerThreadGate {
    @FunctionalInterface public interface CheckedAction { void run() throws Exception; }
    @FunctionalInterface public interface CheckedSupplier<T> { T get() throws Exception; }

    private final BooleanSupplier isServerThread;
    private final Consumer<Runnable> scheduler;

    public ServerThreadGate(BooleanSupplier isServerThread, Consumer<Runnable> scheduler) {
        this.isServerThread = Objects.requireNonNull(isServerThread);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void run(CheckedAction action) throws Exception {
        call(() -> {
            action.run();
            return null;
        });
    }

    public <T> T call(CheckedSupplier<T> supplier) throws Exception {
        if (isServerThread.getAsBoolean()) {
            return supplier.get();
        }
        CompletableFuture<T> completion = new CompletableFuture<>();
        scheduler.accept(() -> {
            try {
                completion.complete(supplier.get());
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        });
        try {
            return completion.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception checked) throw checked;
            throw new IllegalStateException(cause);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Server-thread operation timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
}
