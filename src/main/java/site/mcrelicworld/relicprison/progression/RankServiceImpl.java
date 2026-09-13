package site.mcrelicworld.relicprison.progression;

import site.mcrelicworld.relicprison.api.RankService;
import site.mcrelicworld.relicprison.api.model.RankView;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RankServiceImpl implements RankService {
    private final RankRepository repository;
    private volatile List<RankDefinition> ordered = List.of();
    private volatile Map<String, RankDefinition> byId = Map.of();

    public RankServiceImpl(RankRepository repository) { this.repository = repository; }

    public List<RankDefinition> previewLoad(String groupFormat) throws Exception { return repository.load(groupFormat); }
    public void load(String groupFormat) throws Exception { apply(previewLoad(groupFormat)); }
    public void apply(List<RankDefinition> ranks) {
        LinkedHashMap<String, RankDefinition> index = new LinkedHashMap<>();
        ranks.forEach(rank -> index.put(rank.id(), rank));
        ordered = List.copyOf(ranks);
        byId = Map.copyOf(index);
    }

    public Optional<RankDefinition> definition(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<RankDefinition> next(String currentId) {
        RankDefinition current = definition(currentId).orElse(null);
        if (current == null) return Optional.empty();
        int index = ordered.indexOf(current);
        if (index < 0) return Optional.empty();
        for (int next = index + 1; next < ordered.size(); next++) if (ordered.get(next).enabled()) return Optional.of(ordered.get(next));
        return Optional.empty();
    }

    public Optional<RankDefinition> previous(String currentId) {
        RankDefinition current = definition(currentId).orElse(null);
        if (current == null) return Optional.empty();
        int index = ordered.indexOf(current);
        if (index <= 0) return Optional.empty();
        for (int previous = index - 1; previous >= 0; previous--) if (ordered.get(previous).enabled()) return Optional.of(ordered.get(previous));
        return Optional.empty();
    }

    public RankDefinition first() { return ordered.stream().filter(RankDefinition::enabled).findFirst().orElseThrow(); }
    public RankDefinition last() { return ordered.reversed().stream().filter(RankDefinition::enabled).findFirst().orElseThrow(); }
    public int indexOf(String id) {
        RankDefinition definition = definition(id).orElse(null);
        return definition == null ? -1 : ordered.indexOf(definition);
    }
    public List<RankDefinition> definitions() { return ordered; }

    public BigDecimal cumulativeCost(String currentId, String targetId, BigDecimal multiplier) {
        int current = indexOf(currentId);
        int target = indexOf(targetId);
        if (current < 0 || target <= current) throw new IllegalArgumentException("Target rank must be after current rank");
        BigDecimal total = BigDecimal.ZERO;
        for (int index = current; index < target; index++) total = total.add(ordered.get(index).nextCost());
        return total.multiply(multiplier);
    }

    @Override public Optional<RankView> rank(String id) { return definition(id).map(RankDefinition::view); }
    @Override public Collection<RankView> ranks() { return ordered.stream().map(RankDefinition::view).toList(); }
}
