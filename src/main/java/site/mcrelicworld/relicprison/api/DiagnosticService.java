package site.mcrelicworld.relicprison.api;

import site.mcrelicworld.relicprison.api.model.DiagnosticSnapshot;

/** Stable contract for later health and safe-export diagnostics. */
public interface DiagnosticService {
    /** Thread-safe. Returns an immutable diagnostic snapshot. */
    DiagnosticSnapshot snapshot();
}
