package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * The façade over the economy's backend set. A configured currency resolves to its own backend and reads a
 * seeded balance; a bare backend id with no configured currency mints a synthetic currency over that backend;
 * an unknown spec stays an unavailable no-op; and a spec resolved before the registries are supplied answers
 * unavailable without caching, so it heals once the economy wires.
 */
class CurrenciesBackedTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final UUID ALICE = UUID.randomUUID();

    @Test
    void aConfiguredCurrencyIdResolvesToItsOwnBackend() {
        FakeCurrencyBackend ledger = new FakeCurrencyBackend("native");
        ledger.seed(ALICE, 40.0);
        Currencies currencies = new Currencies(() -> backends(ledger), SILENT, "coins");

        assertThat(currencies.resolve("coins").balance(ALICE)).isEqualTo(40.0);
    }

    @Test
    void aBackendIdWithNoConfiguredCurrencyResolvesToASyntheticOne() {
        Currencies currencies = new Currencies(
                () -> backends(new FakeCurrencyBackend("native"), new FakeCurrencyBackend("exp")), SILENT, "coins");

        assertThat(currencies.resolve("exp").available()).isTrue();
        assertThat(currencies.resolve("exp").id()).isEqualTo("exp");
    }

    @Test
    void anUnknownSpecStaysAnUnavailableNoOp() {
        Currencies currencies = new Currencies(() -> backends(new FakeCurrencyBackend("native")), SILENT, "coins");

        assertThat(currencies.resolve("nonsense").available()).isFalse();
        assertThat(currencies.resolve("nonsense").withdraw(ALICE, 5)).isFalse();
    }

    @Test
    void aSpecResolvedBeforeTheRegistriesAreSuppliedIsNotCached() {
        AtomicReference<EconomyBackends> ref = new AtomicReference<>();
        Currencies currencies = new Currencies(ref::get, SILENT, "coins");

        assertThat(currencies.resolve("coins").available())
                .as("no provider is available while the economy is still unwired")
                .isFalse();

        FakeCurrencyBackend ledger = new FakeCurrencyBackend("native");
        ledger.seed(ALICE, 25.0);
        ref.set(backends(ledger));

        assertThat(currencies.resolve("coins").available())
                .as("the earlier unavailable answer was not cached, so the same spec now resolves")
                .isTrue();
        assertThat(currencies.resolve("coins").balance(ALICE)).isEqualTo(25.0);
    }

    private static EconomyBackends backends(CurrencyBackend... backends) {
        return new EconomyBackends(
                CurrencyBackendRegistry.of(List.of(backends)),
                CurrencyRegistry.of(List.of(COINS), CurrencyId.of("coins")));
    }

    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
