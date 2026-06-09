package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@link Loan} aggregate is immutable: a payment returns a new instance, the final payment settles the
 * exact residual, an over-offer is trimmed to the residual, and applying more than the balance is refused.
 * Generated ids are unique full UUIDs (the old substring+millis scheme could collide within one tick).
 */
class LoanTest {

    private final PlayerRef debtor = new PlayerRef(UUID.randomUUID(), "Debtor");

    private Loan loan(long total, int installments) {
        return Loan.open(
                debtor,
                Money.of(Currencies.COINS, total),
                Money.of(Currencies.COINS, total),
                new BigDecimal("0.00"),
                installments,
                Money.of(Currencies.COINS, total / installments),
                0L,
                1_000L);
    }

    @Test
    void afterPaymentReturnsANewInstanceAndLeavesTheOriginalUnchanged() {
        Loan original = loan(1000, 4);

        Loan afterOne = original.afterPayment(Money.of(Currencies.COINS, 250));

        assertThat(afterOne).isNotSameAs(original);
        assertThat(original.remainingAmount()).isEqualTo(Money.of(Currencies.COINS, 1000));
        assertThat(afterOne.remainingAmount()).isEqualTo(Money.of(Currencies.COINS, 750));
        assertThat(afterOne.remainingInstallments()).isEqualTo(3);
    }

    @Test
    void settlementForTrimsAnOverOfferToTheResidual() {
        Loan single = loan(1000, 1);

        Money settlement = single.settlementFor(Money.of(Currencies.COINS, 9_999));

        assertThat(settlement).isEqualTo(Money.of(Currencies.COINS, 1000));
        assertThat(single.afterPayment(settlement).isFullyPaid()).isTrue();
    }

    @Test
    void afterPaymentRefusesMoreThanTheRemainingBalance() {
        Loan single = loan(1000, 1);

        assertThatThrownBy(() -> single.afterPayment(Money.of(Currencies.COINS, 1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFinalPaymentZeroesBothBalanceAndInstallments() {
        Loan single = loan(1000, 1);

        Loan settled = single.afterPayment(single.settlementFor(Money.of(Currencies.COINS, 1000)));

        assertThat(settled.isFullyPaid()).isTrue();
        assertThat(settled.remainingInstallments()).isZero();
    }

    @Test
    void generatedIdsAreUniqueFullUuids() {
        String a = Loan.generateId();
        String b = Loan.generateId();

        assertThat(a).isNotEqualTo(b);
        assertThat(a).hasSize(36);
    }

    @Test
    void constructionRejectsACrossCurrencyMismatch() {
        assertThatThrownBy(() -> new Loan(
                        "id",
                        debtor,
                        Money.of(Currencies.COINS, 100),
                        Money.of(Currencies.GEMS, 100),
                        BigDecimal.ZERO,
                        1,
                        Money.of(Currencies.COINS, 100),
                        0L,
                        0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
