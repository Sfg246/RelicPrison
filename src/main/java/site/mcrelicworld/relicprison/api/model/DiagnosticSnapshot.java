package site.mcrelicworld.relicprison.api.model;

import java.util.Map;

public record DiagnosticSnapshot(long createdAt, Map<String, String> values) {
    public DiagnosticSnapshot {
        values = Map.copyOf(values);
    }
}
