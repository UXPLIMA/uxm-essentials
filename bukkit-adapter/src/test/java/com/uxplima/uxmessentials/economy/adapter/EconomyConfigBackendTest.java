package com.uxplima.uxmessentials.economy.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * The economy config reads each currency's {@code currencies.<id>.backend}, defaulting to the native ledger,
 * and reads {@code allow-nonatomic-recurring}, which gates whether a scheduled charge may run against a
 * currency whose backend cannot guard its take.
 */
class EconomyConfigBackendTest {

    @Test
    void aCurrencyWithNoBackendKeyDefaultsToTheNativeLedger() {
        EconomyConfig config = new EconomyConfig(TestConfig.of("currencies.coins.symbol", "$"));
        assertThat(config.currencies().defaultCurrency().backendId()).isEqualTo("native");
    }

    @Test
    void aCurrencyCarriesItsConfiguredBackend() {
        EconomyConfig config = new EconomyConfig(
                TestConfig.of("wallet.default-currency", "points", "currencies.points.backend", "playerpoints"));
        assertThat(config.currencies().defaultCurrency().backendId()).isEqualTo("playerpoints");
    }

    @Test
    void allowNonAtomicRecurringDefaultsToFalse() {
        assertThat(new EconomyConfig(TestConfig.empty()).allowNonAtomicRecurring())
                .isFalse();
    }

    /**
     * A config whose string values come from a fixed map, addressed by full dotted path; every other getter
     * returns the caller's fallback. Enough to exercise the currency-backend and the recurring-gate reads,
     * which touch only string paths and the boolean default.
     */
    private record TestConfig(Map<String, String> strings) implements ConfigStore {

        static TestConfig empty() {
            return new TestConfig(Map.of());
        }

        static TestConfig of(String... pathsAndValues) {
            if (pathsAndValues.length % 2 != 0) {
                throw new IllegalArgumentException("expected alternating path/value pairs");
            }
            Map<String, String> strings = new HashMap<>();
            for (int i = 0; i < pathsAndValues.length; i += 2) {
                strings.put(pathsAndValues[i], pathsAndValues[i + 1]);
            }
            return new TestConfig(strings);
        }

        @Override
        public String getString(String path, String fallback) {
            return strings.getOrDefault(Objects.requireNonNull(path, "path"), fallback);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
