package com.uxplima.uxmessentials.villagers.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link VillagersModule} feature-module contract: it reports the {@code villagers} id, ships enabled by
 * default, honours an explicit {@code modules.villagers.enabled = false}, and contributes no declarative command,
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
    void shipsEnabledByDefaultAndHonoursAnExplicitOptOut() {
        VillagersModule module = new VillagersModule();

        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.villagers.enabled", false))))
                .isFalse();
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
