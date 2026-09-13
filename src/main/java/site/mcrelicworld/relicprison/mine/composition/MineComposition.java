package site.mcrelicworld.relicprison.mine.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MineComposition {
    private final List<CompositionEntry> entries;
    private final CompiledComposition compiled;
    private final Map<String, Double> weights;

    public MineComposition(List<CompositionEntry> entries) {
        if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("Composition cannot be empty");
        List<CompositionEntry> copy = List.copyOf(entries);
        LinkedHashMap<String, Double> map = new LinkedHashMap<>();
        for (CompositionEntry entry : copy) {
            String key = entry.block().qualifiedId();
            if (map.put(key, entry.weight()) != null) {
                throw new IllegalArgumentException("Duplicate block type: " + key);
            }
        }
        this.entries = copy;
        this.compiled = new CompiledComposition(copy);
        this.weights = Collections.unmodifiableMap(map);
    }

    public static MineComposition defaultStone() {
        return new MineComposition(List.of(new CompositionEntry(BlockTypeRef.parse("STONE"), 100.0)));
    }

    public List<CompositionEntry> entries() { return entries; }
    public CompiledComposition compiled() { return compiled; }
    public Map<String, Double> weights() { return weights; }
    public double totalWeight() { return entries.stream().mapToDouble(CompositionEntry::weight).sum(); }

    public MineComposition with(String blockId, double weight) {
        BlockTypeRef block = BlockTypeRef.parse(blockId);
        List<CompositionEntry> updated = new ArrayList<>();
        boolean replaced = false;
        for (CompositionEntry entry : entries) {
            if (entry.block().qualifiedId().equalsIgnoreCase(block.qualifiedId())) {
                updated.add(new CompositionEntry(block, weight, entry.minimumPrestige(), entry.properties(), entry.explicitAir()));
                replaced = true;
            } else updated.add(entry);
        }
        if (!replaced) updated.add(new CompositionEntry(block, weight));
        return new MineComposition(updated);
    }

    public MineComposition without(String blockId) {
        String qualifiedId = BlockTypeRef.parse(blockId).qualifiedId();
        List<CompositionEntry> updated = entries.stream()
                .filter(entry -> !entry.block().qualifiedId().equalsIgnoreCase(qualifiedId))
                .toList();
        if (updated.isEmpty()) throw new IllegalArgumentException("A mine must contain at least one block type");
        return new MineComposition(updated);
    }

    public MineComposition normalized() {
        double total = totalWeight();
        List<CompositionEntry> normalized = entries.stream()
                .map(entry -> new CompositionEntry(entry.block(), entry.weight() / total * 100.0,
                        entry.minimumPrestige(), entry.properties(), entry.explicitAir()))
                .toList();
        return new MineComposition(normalized);
    }
}
