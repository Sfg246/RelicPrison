package site.mcrelicworld.relicprison.admin;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ServerThreadGateTest {
    @Test
    void workerReloadIsMarshalledToServerThread() throws Exception {
        Thread serverThread = Thread.currentThread();
        ArrayBlockingQueue<Runnable> scheduled = new ArrayBlockingQueue<>(1);
        ServerThreadGate gate = new ServerThreadGate(() -> Thread.currentThread() == serverThread, scheduled::add);
        AtomicReference<Thread> executed = new AtomicReference<>();

        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            try { gate.run(() -> executed.set(Thread.currentThread())); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
        scheduled.poll(5, TimeUnit.SECONDS).run();
        worker.get(5, TimeUnit.SECONDS);

        assertEquals(serverThread, executed.get());
    }

    @Test
    void workerLookupReturnsServerThreadValue() throws Exception {
        Thread serverThread = Thread.currentThread();
        ArrayBlockingQueue<Runnable> scheduled = new ArrayBlockingQueue<>(1);
        ServerThreadGate gate = new ServerThreadGate(() -> Thread.currentThread() == serverThread, scheduled::add);

        CompletableFuture<String> worker = CompletableFuture.supplyAsync(() -> {
            try { return gate.call(() -> Thread.currentThread().getName()); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
        scheduled.poll(5, TimeUnit.SECONDS).run();

        assertEquals(serverThread.getName(), worker.get(5, TimeUnit.SECONDS));
    }
}
