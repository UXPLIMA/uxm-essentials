package com.uxplima.uxmessentials.ranks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RanksConfig}'s resolution from the module's scoped config: an empty store yields the shipped
 * defaults (module on, prestige and autorank off — both opt-in), and explicit keys override each switch.
 */
class RanksConfigTest {

    @Test
    void anEmptyStoreYieldsTheShippedDefaults() {
        RanksConfig config = RanksConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.prestigeEnabled()).isFalse();
        assertThat(config.autorankEnabled()).isFalse();
    }

    @Test
    void explicitOverridesAreReadBack() {
        RanksConfig config = RanksConfig.from(
                new FixedConfig(Map.of("enabled", false, "prestige.enabled", true, "autorank.enabled", true)));

        assertThat(config.enabled()).isFalse();
        assertThat(config.prestigeEnabled()).isTrue();
        assertThat(config.autorankEnabled()).isTrue();
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
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
