package com.uxplima.uxmessentials.ranks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RanksConfig}'s resolution from the module's scoped config: an empty store yields the shipped
 * defaults (module on, prestige and autorank off — both opt-in — with a no-cap, free, no-bonus prestige), and
 * explicit keys override each switch and every prestige tunable.
 */
class RanksConfigTest {

    @Test
    void anEmptyStoreYieldsTheShippedDefaults() {
        RanksConfig config = RanksConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.prestige().enabled()).isFalse();
        assertThat(config.prestige().maxLevel()).isZero();
        assertThat(config.prestige().cost()).isZero();
        assertThat(config.prestige().requirements()).isEmpty();
        assertThat(config.prestige().actions()).isEmpty();
        assertThat(config.prestige().rewardMultiplier()).isEqualTo(1.0);
        assertThat(config.autorank().enabled()).isFalse();
        assertThat(config.autorank().intervalSeconds()).isEqualTo(300);
        assertThat(config.autorank().chargeCost()).isTrue();
        assertThat(config.gui().enabled()).isTrue();
    }

    @Test
    void theGuiGateDefaultsOnAndReadsBackWhenTurnedOff() {
        assertThat(RanksConfig.from(new FixedConfig(Map.of())).gui().enabled()).isTrue();
        assertThat(RanksConfig.from(new FixedConfig(Map.of("gui.enabled", false)))
                        .gui()
                        .enabled())
                .isFalse();
    }

    @Test
    void explicitOverridesAreReadBack() {
        RanksConfig config = RanksConfig.from(new FixedConfig(Map.of(
                "enabled",
                false,
                "prestige.enabled",
                true,
                "prestige.max-level",
                10,
                "prestige.cost",
                2500,
                "prestige.requirements",
                List.of("money 2500"),
                "prestige.actions",
                List.of("message prestiged"),
                "prestige.reward-multiplier",
                1.5,
                "autorank.enabled",
                true,
                "autorank.interval-seconds",
                600,
                "autorank.charge-cost",
                false)));

        assertThat(config.enabled()).isFalse();
        assertThat(config.prestige().enabled()).isTrue();
        assertThat(config.prestige().maxLevel()).isEqualTo(10);
        assertThat(config.prestige().cost()).isEqualTo(2500L);
        assertThat(config.prestige().requirements()).containsExactly("money 2500");
        assertThat(config.prestige().actions()).containsExactly("message prestiged");
        assertThat(config.prestige().rewardMultiplier()).isEqualTo(1.5);
        assertThat(config.autorank().enabled()).isTrue();
        assertThat(config.autorank().intervalSeconds()).isEqualTo(600);
        assertThat(config.autorank().chargeCost()).isFalse();
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

        @Override
        public long getLong(String path, long fallback) {
            return values.get(path) instanceof Number n ? n.longValue() : fallback;
        }

        @Override
        public double getDouble(String path, double fallback) {
            return values.get(path) instanceof Number n ? n.doubleValue() : fallback;
        }

        @Override
        @SuppressWarnings("unchecked") // The test map stores only List<String> under list paths.
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> list ? List.copyOf((List<String>) list) : fallback;
        }
    }
}
