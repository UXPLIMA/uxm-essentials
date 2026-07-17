package com.uxplima.uxmessentials.villagers.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the villagers module's typed config view: the module ships enabled while every trade-availability feature ships
 * off (so a fresh install is inert), the restock interval defaults to ten minutes and is clamped to at least one
 * second, and explicit overrides are read back.
 */
class VillagersConfigTest {

    @Test
    void moduleEnabledButEveryFeatureOffByDefault() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.infiniteTrading().enabled()).isFalse();
        assertThat(config.restock().enabled()).isFalse();
        assertThat(config.instantRestock().enabled()).isFalse();
        assertThat(config.disableTrades().enabled()).isFalse();
    }

    @Test
    void restockIntervalDefaultsToTenMinutes() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of()));

        assertThat(config.restock().intervalSeconds()).isEqualTo(600);
        assertThat(config.restock().interval()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void restockIntervalIsClampedToAtLeastOneSecond() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of("restock.interval-seconds", 0)));

        assertThat(config.restock().intervalSeconds()).isEqualTo(1);
        assertThat(config.restock().interval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void explicitOverridesAreReadBack() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of(
                "enabled", false,
                "infinite-trading.enabled", true,
                "restock.enabled", true,
                "restock.interval-seconds", 120,
                "instant-restock.enabled", true,
                "disable-trades.enabled", true)));

        assertThat(config.enabled()).isFalse();
        assertThat(config.infiniteTrading().enabled()).isTrue();
        assertThat(config.restock().enabled()).isTrue();
        assertThat(config.restock().intervalSeconds()).isEqualTo(120);
        assertThat(config.instantRestock().enabled()).isTrue();
        assertThat(config.disableTrades().enabled()).isTrue();
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
