package site.mcrelicworld.relicprison.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class PerformanceMetrics {
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public void increment(String metric) {
        increment(metric, 1L);
    }

    public void increment(String metric, long amount) {
        counters.computeIfAbsent(metric, ignored -> new Counter()).count.add(Math.max(0L, amount));
    }

    public void recordNanos(String metric, long nanos) {
        Counter counter = counters.computeIfAbsent(metric, ignored -> new Counter());
        counter.count.increment();
        counter.totalNanos.add(Math.max(0L, nanos));
        counter.maximumNanos.accumulate(Math.max(0L, nanos));
    }

    public Map<String, String> snapshot() {
        Map<String, String> values = new LinkedHashMap<>();
        counters.keySet().stream().sorted().forEach(key -> {
            Counter counter = counters.get(key);
            if (counter == null) return;
            long count = counter.count.sum();
            long total = counter.totalNanos.sum();
            long max = counter.maximumNanos.sum();
            values.put("metric." + key + ".count", String.valueOf(count));
            if (total > 0L || max > 0L) {
                long average = count <= 0L ? 0L : total / count;
                values.put("metric." + key + ".avg-ms", millis(average));
                values.put("metric." + key + ".max-ms", millis(max));
            }
        });
        return Map.copyOf(values);
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.US, "%.3f", nanos / 1_000_000.0D);
    }

    private static final class Counter {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAccumulator maximumNanos = new LongAccumulator();
    }

    private static final class LongAccumulator {
        private final java.util.concurrent.atomic.AtomicLong value = new java.util.concurrent.atomic.AtomicLong();

        void accumulate(long candidate) {
            value.accumulateAndGet(candidate, Math::max);
        }

        long sum() {
            return value.get();
        }
    }
}
