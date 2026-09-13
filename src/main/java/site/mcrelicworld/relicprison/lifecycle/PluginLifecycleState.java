package site.mcrelicworld.relicprison.lifecycle;

public enum PluginLifecycleState {
    BOOTSTRAPPING,
    DATABASE_INITIALIZING,
    LOADING_SERVICES,
    READY,
    DEGRADED,
    DISABLED
}
