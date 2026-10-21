package com.uxplima.uxmessentials.playerwarps.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * Player-warp rent is a charge on a timer, and a timer charge on a backend that cannot guard its take may charge
 * twice: a debit that reports failure might have succeeded, and the next sweep sees the money still owed. The
 * economy configuration has promised since the gate was written that such a charge is refused at startup unless
 * the operator sets {@code allow-nonatomic-recurring}, but rent landed later and never asked the gate.
 */
class PlayerWarpRentHoldsTheRecurringGateTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency POINTS =
            Currency.builder(CurrencyId.of("points")).backendId("playerpoints").build();
    private static final CurrencyRegistry CURRENCIES =
            CurrencyRegistry.of(List.of(COINS, POINTS), CurrencyId.of("coins"));

    private final RecordingLog log = new RecordingLog();

    private static CurrencyBackend backend(String id, boolean atomic) {
        CurrencyBackend backend = mock(CurrencyBackend.class);
        when(backend.id()).thenReturn(id);
        when(backend.atomicDebit()).thenReturn(atomic);
        when(backend.available()).thenReturn(true);
        return backend;
    }

    private static final CurrencyBackendRegistry BACKENDS =
            CurrencyBackendRegistry.of(List.of(backend("native", true), backend("playerpoints", false)));

    @Test
    void rentOnTheBuiltInLedgerRuns() {
        assertThat(PlayerwarpsWiring.rentChargeAccepted("coins", CURRENCIES, BACKENDS, false, log))
                .isTrue();
        assertThat(log.errors).isEmpty();
    }

    @Test
    void rentOnABackendThatCannotGuardItsTakeIsRefusedAndNamed() {
        assertThat(PlayerwarpsWiring.rentChargeAccepted("points", CURRENCIES, BACKENDS, false, log))
                .isFalse();
        assertThat(log.errors).singleElement().asString().contains("points").contains("allow-nonatomic-recurring");
    }

    @Test
    void theOperatorMayAcceptTheRisk() {
        assertThat(PlayerwarpsWiring.rentChargeAccepted("points", CURRENCIES, BACKENDS, true, log))
                .isTrue();
    }

    @Test
    void rentInACurrencyNobodyDefinedIsRefused() {
        assertThat(PlayerwarpsWiring.rentChargeAccepted("doubloons", CURRENCIES, BACKENDS, false, log))
                .isFalse();
        assertThat(log.errors).singleElement().asString().contains("doubloons");
    }

    private static final class RecordingLog implements Logger {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message + " " + cause.getMessage());
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
