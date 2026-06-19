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
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LinkedWorldEntryFeeTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();

    private final EconomyProvider provider = mock(EconomyProvider.class);
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Steve");

    private final AtomicReference<@Nullable EconomyProvider> providerSlot = new AtomicReference<>();
    private final AtomicReference<@Nullable Currency> currencySlot = new AtomicReference<>();
    private final LinkedWorldEntryFee fee = new LinkedWorldEntryFee(providerSlot::get, currencySlot::get);

    @Test
    void canAffordIsFreeWhenBothSuppliersAreNull() {
        assertThat(fee.canAfford(who, new BigDecimal("100.00"))).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    void chargeIsFreeWhenBothSuppliersAreNull() {
        assertThat(fee.charge(who, new BigDecimal("1000000.00"))).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    void resolvesToFreeWhenOnlyTheProviderIsAvailable() {
        providerSlot.set(provider);

        assertThat(fee.canAfford(who, new BigDecimal("100.00"))).isTrue();
        assertThat(fee.charge(who, new BigDecimal("100.00"))).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    void resolvesToFreeWhenOnlyTheCurrencyIsAvailable() {
        currencySlot.set(COINS);

        assertThat(fee.canAfford(who, new BigDecimal("100.00"))).isTrue();
        assertThat(fee.charge(who, new BigDecimal("100.00"))).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    void canAffordReflectsTheProviderBalanceWhenSuppliersArePopulated() {
        providerSlot.set(provider);
        currencySlot.set(COINS);
        when(provider.balance(who, COINS)).thenReturn(Money.of(COINS, new BigDecimal("150.00")));

        assertThat(fee.canAfford(who, new BigDecimal("100.00"))).isTrue();
        assertThat(fee.canAfford(who, new BigDecimal("150.00"))).isTrue();
        assertThat(fee.canAfford(who, new BigDecimal("150.01"))).isFalse();
    }

    @Test
    void chargeDelegatesToTheProviderDebitWhenSuppliersArePopulated() {
        providerSlot.set(provider);
        currencySlot.set(COINS);
        when(provider.debit(eq(who), any())).thenReturn(Result.ok());

        assertThat(fee.charge(who, new BigDecimal("100.00"))).isTrue();
        verify(provider).debit(who, Money.of(COINS, new BigDecimal("100.00")));
    }

    @Test
    void chargeReportsFailureWhenTheProviderRejectsTheDebit() {
        providerSlot.set(provider);
        currencySlot.set(COINS);
        when(provider.debit(eq(who), any())).thenReturn(Result.err(TransferError.INSUFFICIENT_FUNDS));

        assertThat(fee.charge(who, new BigDecimal("100.00"))).isFalse();
        verify(provider).debit(who, Money.of(COINS, new BigDecimal("100.00")));
    }

    @Test
    void resolvesPerCallSoFlippingFromDisabledToEnabledStartsChargingTheSameInstance() {
        // Economy not up yet: the same instance must behave as free and never touch a provider.
        assertThat(fee.canAfford(who, new BigDecimal("100.00"))).isTrue();
        assertThat(fee.charge(who, new BigDecimal("100.00"))).isTrue();
        verifyNoInteractions(provider);

        // Economy comes up after worlds was wired: the suppliers now yield the live provider/currency.
        providerSlot.set(provider);
        currencySlot.set(COINS);
        when(provider.balance(who, COINS)).thenReturn(Money.of(COINS, new BigDecimal("50.00")));
        when(provider.debit(eq(who), any())).thenReturn(Result.ok());

        // The very same instance now delegates to the economy path rather than staying free.
        assertThat(fee.canAfford(who, new BigDecimal("100.00"))).isFalse();
        assertThat(fee.charge(who, new BigDecimal("40.00"))).isTrue();
        verify(provider).debit(who, Money.of(COINS, new BigDecimal("40.00")));
    }
}
