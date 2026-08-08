package com.uxplima.uxmessentials.villagers.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link VillagersModule} feature-module contract: it reports the {@code villagers} id, ships disabled by
 * default, honours an explicit {@code modules.villagers.enabled = true}, and contributes no declarative command,
 * listener, or migration (those are adapter-wired). The registry-level wiring is covered by
 * {@code FeatureModuleRegistryDriftTest}.
 */
class VillagersModuleTest {

    @Test
    void reportsItsIdAndConfigRoot() {
        VillagersModule module = new VillagersModule();

        assertThat(module.id()).isEqualTo(ModuleId.of("villagers"));
        assertThat(module.configRoot()).isEqualTo("modules.villagers");
    }

    @Test
    void shipsDisabledByDefaultAndHonoursAnExplicitOptIn() {
        VillagersModule module = new VillagersModule();

        // What a villager trades and how often it restocks is an economy decision, left to the operator.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isFalse();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.villagers.enabled", true))))
                .isTrue();
    }

    @Test
    void contributesNoDeclarativeCommandListenerOrMigration() {
        VillagersModule module = new VillagersModule();

        assertThat(module.commands()).isEmpty();
        assertThat(module.listeners()).isEmpty();
        assertThat(module.migrations()).isEmpty();
    }

    /** A map-backed {@link ConfigStore} for driving the enable gate. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
