package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Loan.CreditScore;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Loans through the real migrations: a loan exists only with its payout, and a payment only with its money. */
@EveryBackend
class JooqLoanRepositoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).max(new BigDecimal("1000")).build();

    private JooqLoanRepository loans;
    private JooqWalletRepository wallets;

    @BeforeEach
    void open(TestDatabase database) {
        CurrencyRegistry currencies = CurrencyRegistry.of(List.of(COINS), COINS.id());
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        loans = new JooqLoanRepository(database.dsl(), currencies, clock);
        wallets = new JooqWalletRepository(database.dsl(), currencies, clock);
    }

    @Test
    void aDisbursedLoanIsPaidOutAndStoredWithItsTerms() {
        Loan loan = loan("100", "110", 4);

        assertThat(loans.disburse(loan).isOk()).isTrue();

        assertThat(walletBalance()).isEqualByComparingTo("100");
        Loan stored = loans.findById(loan.id()).orElseThrow();
        assertThat(stored.remainingAmount().amount()).isEqualByComparingTo("110");
        assertThat(stored.interestRate()).isEqualByComparingTo("0.1");
        assertThat(stored.remainingInstallments()).isEqualTo(4);
        assertThat(loans.findByDebtor(ALEX)).extracting(Loan::id).containsExactly(loan.id());
        assertThat(loans.findAllActive()).extracting(Loan::id).containsExactly(loan.id());
    }

    @Test
    void aLoanWhosePayoutWouldOverfillTheWalletIsNotTaken() {
        wallets.credit(ALEX, coins("950"));
        Loan loan = loan("100", "110", 4);

        assertThat(loans.disburse(loan).errorOrThrow()).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);

        assertThat(loans.findById(loan.id())).isEmpty();
        assertThat(walletBalance()).isEqualByComparingTo("950");
    }

    @Test
    void aPaymentLowersTheDebtAndTheLastOneClosesTheLoan() {
        Loan loan = loan("100", "110", 2);
        loans.disburse(loan);
        Loan half = loan.afterPayment(coins("55"));

        assertThat(loans.applyRepayment(ALEX, coins("55"), half, false).isOk()).isTrue();
        assertThat(loans.findById(loan.id()).orElseThrow().remainingAmount().amount())
                .isEqualByComparingTo("55");

        wallets.credit(ALEX, coins("10"));
        assertThat(loans.applyRepayment(ALEX, coins("55"), half.afterPayment(coins("55")), true)
                        .isOk())
                .isTrue();
        assertThat(loans.findById(loan.id())).isEmpty();
        assertThat(walletBalance()).isEqualByComparingTo("0");
    }

    @Test
    void aPaymentTheWalletCannotCoverRecordsNothing() {
        Loan loan = loan("100", "110", 2);
        loans.disburse(loan);
        wallets.debit(ALEX, coins("90"));

        assertThat(loans.applyRepayment(ALEX, coins("55"), loan.afterPayment(coins("55")), false)
                        .errorOrThrow())
                .isEqualTo(TransferError.INSUFFICIENT_FUNDS);

        assertThat(loans.findById(loan.id()).orElseThrow().remainingAmount().amount())
                .isEqualByComparingTo("110");
        assertThat(walletBalance()).isEqualByComparingTo("10");
    }

    @Test
    void aCreditScoreIsKeptAndReplaced() {
        assertThat(loans.getCreditScore(ALEX).score()).isEqualTo(100);

        loans.saveCreditScore(new CreditScore(ALEX, 640, 5_000L));
        loans.saveCreditScore(new CreditScore(ALEX, 655, 6_000L));

        CreditScore score = loans.getCreditScore(ALEX);
        assertThat(score.score()).isEqualTo(655);
        assertThat(score.lastUpdated()).isEqualTo(6_000L);
    }

    private static Loan loan(String principal, String repayable, int installments) {
        BigDecimal total = new BigDecimal(repayable);
        return Loan.open(
                ALEX,
                coins(principal),
                coins(repayable),
                new BigDecimal("0.1"),
                installments,
                Money.of(COINS, total.divide(BigDecimal.valueOf(installments))),
                1_000L,
                2_000L);
    }

    private BigDecimal walletBalance() {
        return wallets.findByOwner(ALEX).orElseThrow().balanceOf(COINS).amount();
    }

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }
}
