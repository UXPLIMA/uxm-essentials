package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import org.junit.jupiter.api.Test;

class RecurringChargePolicyTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency POINTS =
            Currency.builder(CurrencyId.of("points")).backendId("playerpoints").build();

    private static final CurrencyRegistry CURRENCIES =
            CurrencyRegistry.of(List.of(COINS, POINTS), CurrencyId.of("coins"));
    private static final CurrencyBackendRegistry BACKENDS = CurrencyBackendRegistry.of(List.of(
            new FakeCurrencyBackend("native", true, true), new FakeCurrencyBackend("playerpoints", false, true)));

    @Test
    void aRecurringChargeOnTheNativeLedgerIsAlwaysAllowed() {
        assertThatCode(() ->
                        RecurringChargePolicy.validate(CURRENCIES, BACKENDS, Set.of(CurrencyId.of("coins")), false))
                .doesNotThrowAnyException();
    }

    @Test
    void aRecurringChargeOnANonAtomicBackendIsRefusedByDefault() {
        assertThatThrownBy(() ->
                        RecurringChargePolicy.validate(CURRENCIES, BACKENDS, Set.of(CurrencyId.of("points")), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("points")
                .hasMessageContaining("playerpoints")
                .hasMessageContaining("allow-nonatomic-recurring");
    }

    @Test
    void theOperatorCanOptIn() {
        assertThatCode(() ->
                        RecurringChargePolicy.validate(CURRENCIES, BACKENDS, Set.of(CurrencyId.of("points")), true))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnknownCurrencyIdInTheRecurringSetThrows() {
        assertThatThrownBy(() ->
                        RecurringChargePolicy.validate(CURRENCIES, BACKENDS, Set.of(CurrencyId.of("doubloons")), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doubloons");
    }
}
