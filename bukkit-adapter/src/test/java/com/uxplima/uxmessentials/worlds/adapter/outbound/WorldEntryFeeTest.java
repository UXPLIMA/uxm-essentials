package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.Test;

class WorldEntryFeeTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();

    private final EconomyProvider provider = mock(EconomyProvider.class);
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Steve");

    private EconomyWorldEntryFee economyFee() {
        return new EconomyWorldEntryFee(provider, COINS);
    }

    @Test
    void canAffordIsTrueWhenTheBalanceMeetsTheAmount() {
        when(provider.balance(who, COINS)).thenReturn(Money.of(COINS, new BigDecimal("100.00")));

        assertThat(economyFee().canAfford(who, new BigDecimal("100.00"))).isTrue();
    }

    @Test
    void canAffordIsTrueWhenTheBalanceExceedsTheAmount() {
        when(provider.balance(who, COINS)).thenReturn(Money.of(COINS, new BigDecimal("250.00")));

        assertThat(economyFee().canAfford(who, new BigDecimal("100.00"))).isTrue();
    }

    @Test
    void canAffordIsFalseWhenTheBalanceIsBelowTheAmount() {
        when(provider.balance(who, COINS)).thenReturn(Money.of(COINS, new BigDecimal("99.99")));

        assertThat(economyFee().canAfford(who, new BigDecimal("100.00"))).isFalse();
    }

    @Test
    void chargeDebitsTheAmountInTheConfiguredCurrencyAndReportsSuccess() {
        when(provider.debit(eq(who), any())).thenReturn(Result.ok());

        boolean charged = economyFee().charge(who, new BigDecimal("100.00"));

        assertThat(charged).isTrue();
        verify(provider).debit(who, Money.of(COINS, new BigDecimal("100.00")));
    }

    @Test
    void chargeReportsFailureWhenTheDebitIsRejected() {
        when(provider.debit(eq(who), any())).thenReturn(Result.err(TransferError.INSUFFICIENT_FUNDS));

        assertThat(economyFee().charge(who, new BigDecimal("100.00"))).isFalse();
    }

    @Test
    void freeFeeAlwaysAffordsAndChargesWithoutTouchingAnyProvider() {
        FreeWorldEntryFee free = new FreeWorldEntryFee();

        assertThat(free.canAfford(who, new BigDecimal("1000000.00"))).isTrue();
        assertThat(free.charge(who, new BigDecimal("1000000.00"))).isTrue();
        verifyNoInteractions(provider);
    }
}
