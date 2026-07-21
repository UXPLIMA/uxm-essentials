package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the trade module's typed config view: the shipped defaults an operator inherits by deleting a line, the
 * distance-limit helper, that explicit overrides are read back, and the range guards on the numeric tunables.
 */
class TradeConfigTest {

    @Test
    void defaultsMatchTheShippedConfig() {
        TradeConfig config = TradeConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.currenciesAllowed()).containsExactly("coins");
        assertThat(config.itemBlacklist()).isEmpty();
        assertThat(config.requestDistance()).isZero();
        assertThat(config.hasDistanceLimit()).isFalse();
        assertThat(config.cooldownSeconds()).isEqualTo(5);
        assertThat(config.crossServer()).isFalse();
        assertThat(config.slotsPerSide()).isEqualTo(20);
        assertThat(config.requestExpirySeconds()).isEqualTo(60);
        assertThat(config.audit()).isTrue();
    }

    @Test
    void explicitOverridesAreReadBack() {
        TradeConfig config = TradeConfig.from(new FixedConfig(Map.of(
                "enabled",
                false,
                "currencies-allowed",
                List.of("coins", "gems"),
                "item-blacklist",
                List.of("BEDROCK", "COMMAND_BLOCK"),
                "request-distance",
                32,
                "cooldown-seconds",
                10,
                "cross-server",
                true,
                "slots-per-side",
                16,
                "request-expiry-seconds",
                30,
                "audit",
                false)));

        assertThat(config.enabled()).isFalse();
        assertThat(config.currenciesAllowed()).containsExactly("coins", "gems");
        assertThat(config.itemBlacklist()).containsExactly("BEDROCK", "COMMAND_BLOCK");
        assertThat(config.requestDistance()).isEqualTo(32);
        assertThat(config.hasDistanceLimit()).isTrue();
        assertThat(config.cooldownSeconds()).isEqualTo(10);
        assertThat(config.crossServer()).isTrue();
        assertThat(config.slotsPerSide()).isEqualTo(16);
        assertThat(config.requestExpirySeconds()).isEqualTo(30);
        assertThat(config.audit()).isFalse();
    }

    @Test
    void aNegativeCooldownIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TradeConfig.from(new FixedConfig(Map.of("cooldown-seconds", -1))));
    }

    @Test
    void anOutOfRangeSlotCountIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TradeConfig.from(new FixedConfig(Map.of("slots-per-side", 0))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TradeConfig.from(new FixedConfig(Map.of("slots-per-side", 99))));
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
        public List<String> getStringList(String path, List<String> fallback) {
            if (!(values.get(path) instanceof List<?> list)) {
                return fallback;
            }
            return list.stream().map(String::valueOf).toList();
        }
    }
}
