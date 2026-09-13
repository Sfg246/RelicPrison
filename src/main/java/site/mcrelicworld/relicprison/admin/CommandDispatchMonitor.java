package site.mcrelicworld.relicprison.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public final class CommandDispatchMonitor {
    private static final int MAX_RECENT_FAILURES = 50;

    private final RelicPrisonPlugin plugin;
    private final LongAdder attempts = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final ArrayDeque<String> recentFailures = new ArrayDeque<>();

    public CommandDispatchMonitor(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean dispatchConsole(String subsystem, String command) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit commands must be dispatched on the server thread");
        }
        attempts.increment();
        try {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (!success) recordFailure(subsystem, "returned false");
            return success;
        } catch (RuntimeException ex) {
            recordFailure(subsystem, rootMessage(ex));
            return false;
        }
    }

    public void recordFailure(String subsystem, String summary) {
        failures.increment();
        String safe = RedactionService.redactInline(summary == null ? "" : summary);
        synchronized (recentFailures) {
            while (recentFailures.size() >= MAX_RECENT_FAILURES) recentFailures.removeFirst();
            recentFailures.addLast(System.currentTimeMillis() + " " + safeSubsystem(subsystem) + " " + safe);
        }
        plugin.getLogger().warning("RelicPrison command dispatch failed in " + safeSubsystem(subsystem)
                + ": " + safe);
    }

    public long attemptedCommands() {
        return attempts.sum();
    }

    public long failedCommands() {
        return failures.sum();
    }

    public List<String> recentFailures() {
        synchronized (recentFailures) {
            return List.copyOf(new ArrayList<>(recentFailures));
        }
    }

    public void notifyConsoleOnly(String message) {
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(message);
    }

    private static String safeSubsystem(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
