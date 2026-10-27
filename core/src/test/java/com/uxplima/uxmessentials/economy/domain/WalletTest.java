package com.uxplima.uxmessentials.economy.domain;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import org.junit.jupiter.api.Test;

class WalletTest {

    private static final Currency DOLLARS = Currency.builder(CurrencyId.of("dollars"))
            .max(new BigDecimal("1000"))
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .precision(0)
            .min(new BigDecimal("-50"))
            .build();

    private static Money dollars(String amount) {
        return Money.of(DOLLARS, new BigDecimal(amount));
    }

    @Test
    void aCurrencyNeverHeldReadsAsZero() {
        assertThat(Wallet.empty(ALEX).balanceOf(DOLLARS).isZero()).isTrue();
    }

    @Test
    void creditAndDebitMoveTheBalanceAndRaiseEvents() {
        Wallet.Change credited = Wallet.empty(ALEX).credit(dollars("100"), NOW).orElseThrow();
        Wallet.Change debited = credited.result().debit(dollars("30.25"), NOW).orElseThrow();

        assertThat(credited.event()).isInstanceOf(WalletCredited.class);
        assertThat(debited.event()).isInstanceOf(WalletDebited.class);
        assertThat(debited.result().balanceOf(DOLLARS).amount()).isEqualByComparingTo("69.75");
    }

    @Test
    void aDebitBelowTheMinimumIsInsufficientFunds() {
        Wallet wallet = Wallet.of(ALEX, Map.of(DOLLARS, dollars("10")));

        Rejection rejection = wallet.debit(dollars("10.01"), NOW).errorOrThrow();

        assertThat(rejection.error()).isEqualTo(EconomyError.INSUFFICIENT_FUNDS);
        assertThat(wallet.balanceOf(DOLLARS).amount()).isEqualByComparingTo("10");
    }

    @Test
    void aCurrencyWithANegativeMinimumAllowsDebt() {
        Wallet inDebt =
                Wallet.empty(ALEX).debit(Money.of(GEMS, 50), NOW).orElseThrow().result();

        assertThat(inDebt.balanceOf(GEMS).amount()).isEqualByComparingTo("-50");
        assertThat(inDebt.debit(Money.of(GEMS, 1), NOW).asError())
                .hasValueSatisfying(
                        rejection -> assertThat(rejection.error()).isEqualTo(EconomyError.INSUFFICIENT_FUNDS));
    }

    @Test
    void aCreditPastTheMaximumIsRefused() {
        Wallet nearlyFull = Wallet.of(ALEX, Map.of(DOLLARS, dollars("999")));

        assertThat(nearlyFull.credit(dollars("2"), NOW).errorOrThrow().error())
                .isEqualTo(EconomyError.BALANCE_MAX_EXCEEDED);
        assertThat(nearlyFull.credit(dollars("1"), NOW).isOk()).isTrue();
    }

    @Test
    void currenciesAreKeptApart() {
        Wallet wallet =
                Wallet.empty(ALEX).credit(dollars("5"), NOW).orElseThrow().result();

        assertThat(wallet.balanceOf(GEMS).isZero()).isTrue();
        assertThatThrownBy(() -> dollars("1").plus(Money.of(GEMS, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeChangeIsAProgrammingError() {
        assertThatThrownBy(() -> Wallet.empty(ALEX).credit(dollars("-1"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCurrencyWhoseStartingBalanceIsOutsideItsRangeDoesNotExist() {
        assertThatThrownBy(() -> Currency.builder(CurrencyId.of("bad"))
                        .min(BigDecimal.TEN)
                        .starting(BigDecimal.ONE)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
