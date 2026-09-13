package site.mcrelicworld.relicprison.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class VaultEconomyAdapterTest {
    @Test
    void resolvesProviderFromClassBasedRegistration() throws ReflectiveOperationException {
        Object provider = new Object();
        ClassRegistration registration = new ClassRegistration(provider);
        assertTrue(provider == VaultEconomyAdapter.resolveProvider(registration));
    }

    public static final class ClassRegistration {
        private final Object provider;

        ClassRegistration(Object provider) {
            this.provider = provider;
        }

        public Object getProvider() {
            return provider;
        }
    }
}
