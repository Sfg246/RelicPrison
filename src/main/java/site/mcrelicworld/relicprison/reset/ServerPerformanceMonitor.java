package site.mcrelicworld.relicprison.reset;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;

final class ServerPerformanceMonitor {
    private Method averageTickTime;
    private boolean resolved;

    double currentMspt() {
        if (!resolved) resolve();
        if (averageTickTime == null) return 0;
        try {
            Object value = averageTickTime.invoke(Bukkit.getServer());
            return value instanceof Number number ? number.doubleValue() : 0;
        } catch (ReflectiveOperationException ex) {
            averageTickTime = null;
            return 0;
        }
    }

    private void resolve() {
        resolved = true;
        try { averageTickTime = Bukkit.getServer().getClass().getMethod("getAverageTickTime"); }
        catch (ReflectiveOperationException ignored) { averageTickTime = null; }
    }
}
