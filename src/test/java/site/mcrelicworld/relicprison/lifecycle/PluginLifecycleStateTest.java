package site.mcrelicworld.relicprison.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class PluginLifecycleStateTest {
    @Test
    void startupStateMachineOrderIsStable() {
        assertArrayEquals(new PluginLifecycleState[]{
                PluginLifecycleState.BOOTSTRAPPING,
                PluginLifecycleState.DATABASE_INITIALIZING,
                PluginLifecycleState.LOADING_SERVICES,
                PluginLifecycleState.READY,
                PluginLifecycleState.DEGRADED,
                PluginLifecycleState.DISABLED
        }, PluginLifecycleState.values());
    }
}
