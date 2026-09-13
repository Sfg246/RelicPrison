package site.mcrelicworld.relicprison.progression;

import site.mcrelicworld.relicprison.api.PrestigeService;
import site.mcrelicworld.relicprison.api.model.PrestigeView;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PrestigeServiceImpl implements PrestigeService {
    private final PrestigeRepository repository;
    private volatile List<PrestigeDefinition> ordered = List.of();
    private volatile Map<String, PrestigeDefinition> byId = Map.of();

    public PrestigeServiceImpl(PrestigeRepository repository) { this.repository = repository; }
    public List<PrestigeDefinition> previewLoad(String groupFormat) throws Exception { return repository.load(groupFormat); }
    public void load(String groupFormat) throws Exception { apply(previewLoad(groupFormat)); }
    public void apply(List<PrestigeDefinition> prestiges) {
        LinkedHashMap<String, PrestigeDefinition> index = new LinkedHashMap<>();
        prestiges.forEach(prestige -> index.put(prestige.id(), prestige));
        ordered = List.copyOf(prestiges);
        byId = Map.copyOf(index);
    }

    public Optional<PrestigeDefinition> definition(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<PrestigeDefinition> next(String currentId) {
        if (currentId == null) return ordered.stream().filter(PrestigeDefinition::enabled).findFirst();
        PrestigeDefinition current = definition(currentId).orElse(null);
        if (current == null) return Optional.empty();
        int index = ordered.indexOf(current);
        if (index < 0) return Optional.empty();
        for (int next = index + 1; next < ordered.size(); next++) if (ordered.get(next).enabled()) return Optional.of(ordered.get(next));
        return Optional.empty();
    }

    public int indexOf(String id) {
        if (id == null) return -1;
        PrestigeDefinition definition = definition(id).orElse(null);
        return definition == null ? -1 : ordered.indexOf(definition);
    }

    public BigDecimal rankCostMultiplier(String prestigeId) {
        return definition(prestigeId).map(PrestigeDefinition::rankCostMultiplier).orElse(BigDecimal.ONE);
    }

    public List<PrestigeDefinition> definitions() { return ordered; }
    @Override public Optional<PrestigeView> prestige(String id) { return definition(id).map(PrestigeDefinition::view); }
    @Override public Collection<PrestigeView> prestiges() { return ordered.stream().map(PrestigeDefinition::view).toList(); }
}
