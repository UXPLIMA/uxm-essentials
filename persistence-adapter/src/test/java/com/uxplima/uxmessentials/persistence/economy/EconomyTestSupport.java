package com.uxplima.uxmessentials.persistence.economy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Shared fixtures for the economy persistence tests run against the embedded SQLite backend — the tested
 * default of the backend-parity matrix (network backends run the same DSL behind Testcontainers, which this
 * environment may not have). It provides the single-currency {@code coins} default the worked example walks,
 * a random-player helper, and the no-network SQLite {@link ConfigStore} the homes test also uses.
 */
final class EconomyTestSupport {

    /** The default ship-out-of-the-box currency: precision 2, generous max so the contract amounts fit. */
    static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .max(new BigDecimal("1000000000000"))
            .build();

    /** The closed registry the native ledger resolves currency ids through. */
    static final CurrencyRegistry CURRENCIES = CurrencyRegistry.single(COINS);

    private EconomyTestSupport() {}

    static Money coins(long amount) {
        return Money.of(COINS, amount);
    }

    static PlayerRef randomPlayer() {
        return new PlayerRef(
                UUID.randomUUID(), "p" + UUID.randomUUID().toString().substring(0, 6));
    }

    static List<String> baselineMigrations() {
        return List.of("db/migration");
    }

    /** A config that selects the embedded SQLite backend with every default — no network coordinates. */
    record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
