package com.uxplima.uxmessentials.survival.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link SurvivalModule} feature-module contract: it reports the {@code survival} id, ships enabled by
 * default, honours an explicit {@code modules.survival.enabled = false}, and contributes its commands and listeners
 * through the adapter wiring (so the declarative lists are empty) with no migration. The registry-level wiring is
 * covered by {@code FeatureModuleRegistryDriftTest}.
 */
class SurvivalModuleTest {

    @Test
    void reportsItsIdAndConfigRoot() {
        SurvivalModule module = new SurvivalModule();

        assertThat(module.id()).isEqualTo(ModuleId.of("survival"));
        assertThat(module.configRoot()).isEqualTo("modules.survival");
    }

    @Test
    void shipsEnabledByDefaultAndHonoursAnExplicitOptOut() {
        SurvivalModule module = new SurvivalModule();

        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.survival.enabled", false))))
                .isFalse();
    }

    @Test
    void contributesNoDeclarativeCommandListenerOrMigration() {
        SurvivalModule module = new SurvivalModule();

        assertThat(module.commands()).isEmpty();
        assertThat(module.listeners()).isEmpty();
        assertThat(module.migrations()).isEmpty();
    }

    @Test
    void startAndStopTrackTheRunningFlag() {
        SurvivalModule module = new SurvivalModule();
        assertThat(module.isRunning()).isFalse();

        module.stop();
        assertThat(module.isRunning()).isFalse();
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
