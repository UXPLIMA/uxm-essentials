package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.holograms.adapter.outbound.DamageIndicatorConfig.Kind;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class DamageIndicatorConfigTest {

    @Test
    void shipsDisabledWithSensibleDefaults() {
        DamageIndicatorConfig config = DamageIndicatorConfig.fromConfig(new MapConfigStore(Map.of()));

        assertThat(config.enabled()).isFalse();
        assertThat(config.showForPlayers()).isTrue();
        assertThat(config.showForMobs()).isTrue();
        assertThat(config.showHeal()).isTrue();
        assertThat(config.durationTicks()).isEqualTo(20);
    }

    @Test
    void readsOverriddenValues() {
        DamageIndicatorConfig config = DamageIndicatorConfig.fromConfig(new MapConfigStore(Map.of(
                "damage-indicators.enabled", true,
                "damage-indicators.show-for-mobs", false,
                "damage-indicators.duration-ticks", 40)));

        assertThat(config.enabled()).isTrue();
        assertThat(config.showForMobs()).isFalse();
        assertThat(config.durationTicks()).isEqualTo(40);
    }

    @Test
    void formatsDamageWithTheMagnitudeSubstituted() {
        assertThat(DamageIndicatorConfig.disabled().format(12.5, Kind.DAMAGE)).isEqualTo("<red>-12.5");
    }

    @Test
    void dropsATrailingZeroDecimalForAWholeHit() {
        assertThat(DamageIndicatorConfig.disabled().format(7.0, Kind.DAMAGE)).isEqualTo("<red>-7");
    }

    @Test
    void roundsToOneDecimalPlace() {
        assertThat(DamageIndicatorConfig.disabled().format(3.14159, Kind.DAMAGE))
                .isEqualTo("<red>-3.1");
    }

    @Test
    void usesTheCritFormatForACriticalHit() {
        assertThat(DamageIndicatorConfig.disabled().format(10.0, Kind.CRIT)).isEqualTo("<gold><bold>-10 ✦");
    }

    @Test
    void usesTheHealFormatForHealing() {
        assertThat(DamageIndicatorConfig.disabled().format(4.0, Kind.HEAL)).isEqualTo("<green>+4");
    }

    private static final class MapConfigStore implements ConfigStore {
        private final Map<String, Object> values;

        MapConfigStore(Map<String, Object> values) {
            this.values = Map.copyOf(values);
        }

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
