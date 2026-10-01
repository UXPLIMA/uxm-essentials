package com.uxplima.uxmessentials.economy.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import com.uxplima.uxmessentials.economy.application.PublishingEconomyProvider;
import com.uxplima.uxmessentials.economy.application.RoutingEconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exchange, banks and loans stay on while the money is on our own ledger, whatever stands in front of it.
 *
 * <p>The check named the routing provider and nothing else. Putting the provider that publishes the economy's events
 * in front of it would have made every server read as running a foreign economy, and switched the three off.
 */
class RunsOnOurLedgerTest {

    @Test
    @DisplayName("our ledger behind the publisher is still our ledger")
    void theLedgerIsSeenThroughThePublisher() {
        RoutingEconomyProvider routing = routing();

        assertThat(EconomyWiring.runsOnOurLedger(routing)).isTrue();
        assertThat(EconomyWiring.runsOnOurLedger(
                        new PublishingEconomyProvider(routing, event -> {}, Clock.systemUTC())))
                .isTrue();
    }

    @Test
    @DisplayName("a foreign economy is foreign, with or without the publisher")
    void aForeignEconomyIsNotOurs() {
        EconomyProvider foreign = mock(EconomyProvider.class);

        assertThat(EconomyWiring.runsOnOurLedger(foreign)).isFalse();
        assertThat(EconomyWiring.runsOnOurLedger(
                        new PublishingEconomyProvider(foreign, event -> {}, Clock.systemUTC())))
                .isFalse();
    }

    private static RoutingEconomyProvider routing() {
        CurrencyBackend backend = mock(CurrencyBackend.class);
        when(backend.id()).thenReturn("native");
        Currency coins = Currency.builder(CurrencyId.of("coins")).build();
        return new RoutingEconomyProvider(
                CurrencyBackendRegistry.of(List.of(backend)),
                CurrencyRegistry.of(List.of(coins), CurrencyId.of("coins")),
                Clock.systemUTC(),
                mock(Logger.class));
    }
}
