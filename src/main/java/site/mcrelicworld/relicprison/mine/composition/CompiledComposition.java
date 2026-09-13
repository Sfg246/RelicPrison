package site.mcrelicworld.relicprison.mine.composition;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CompiledComposition {
    private final CompositionEntry[] entries;
    private final double[] cumulative;
    private final double total;

    CompiledComposition(List<CompositionEntry> entries) {
        if (entries.isEmpty()) throw new IllegalArgumentException("Composition cannot be empty");
        this.entries = new CompositionEntry[entries.size()];
        this.cumulative = new double[entries.size()];
        double running = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompositionEntry entry = entries.get(i);
            running += entry.weight();
            this.entries[i] = entry;
            cumulative[i] = running;
        }
        this.total = running;
    }

    public BlockTypeRef sample() {
        return sampleEntry().block();
    }

    public CompositionEntry sampleEntry() {
        double roll = ThreadLocalRandom.current().nextDouble(total);
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (roll < cumulative[mid]) high = mid;
            else low = mid + 1;
        }
        return entries[low];
    }

    public BlockTypeRef[] blocks() {
        BlockTypeRef[] blocks = new BlockTypeRef[entries.length];
        for (int index = 0; index < entries.length; index++) blocks[index] = entries[index].block();
        return blocks;
    }
}
