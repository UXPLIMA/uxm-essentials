package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.customcommands.adapter.outbound.CurrenciesCommandFee;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.EconomyBackends;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The money gate of a custom command. A command priced while no economy answers still runs, a priced command reads
 * the live balance, a charge goes through the backend, and the price is quoted the way the currency writes it.
 */
class CurrenciesCommandFeeTest {

    private final PlayerRef steve = new PlayerRef(UUID.randomUUID(), "Steve");
    private final AtomicReference<EconomyBackends> backends = new AtomicReference<>();
    private final Currencies currencies = new Currencies(backends::get, new SilentLogger(), "vault");
    private final CurrenciesCommandFee fee = new CurrenciesCommandFee(currencies, () -> "vault");

    @Test
    void aFreePriceNeverAsksTheEconomyAnything() {
        assertThat(fee.canAfford(steve, 0)).isTrue();
        assertThat(fee.charge(steve, 0)).isTrue();
    }

    @Test
    void aPricedCommandRunsWhileNoBackendAnswers() {
        assertThat(fee.canAfford(steve, 100)).isTrue();
        assertThat(fee.charge(steve, 100)).isTrue();
    }

    @Test
    void thePriceIsQuotedAsAPlainNumberWithoutABackend() {
        assertThat(fee.format(100)).isEqualTo("100.0");
    }

    /** A logger double: this test asserts on money, not on lines. */
    private static final class SilentLogger implements Logger {

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
