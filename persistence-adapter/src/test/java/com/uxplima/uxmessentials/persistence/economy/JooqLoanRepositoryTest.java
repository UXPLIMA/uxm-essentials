package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.COINS;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.CURRENCIES;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The atomic loan money moves against the default embedded SQLite backend. {@code disburse} credits the
 * principal and writes the loan row in one transaction; {@code applyRepayment} guards the debit and applies the
 * loan-row change in one transaction. The load-bearing cases: a disburse the wallet clamp rejects writes no
 * loan row, and a repayment short of funds debits nothing and leaves the loan untouched.
 */
class JooqLoanRepositoryTest {

    private Persistence persistence;
    private JooqLoanRepository loans;
    private JooqWalletRepository wallets;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new EconomyTestSupport.SqliteConfig(),
                dataFolder,
                EconomyTestSupport.baselineMigrations(),
                new EconomyTestSupport.NoopLogger());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        loans = new JooqLoanRepository(persistence.dsl(), CURRENCIES, clock);
        wallets = new JooqWalletRepository(persistence.dsl(), CURRENCIES, clock);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    private Loan loanFor(PlayerRef debtor, long principal, long total, int installments) {
        return Loan.open(
                debtor,
                coins(principal),
                coins(total),
                new BigDecimal("0.10"),
                installments,
                Money.of(COINS, new BigDecimal(total).divide(new BigDecimal(installments))),
                0L,
                1_000L);
    }

    @Test
    void disburseCreditsThePrincipalAndWritesTheLoanAtomically() {
        PlayerRef debtor = randomPlayer();
        Loan loan = loanFor(debtor, 1000, 1100, 4);

        assertThat(loans.disburse(loan).isOk()).isTrue();

        assertThat(wallets.findByOwner(debtor).orElseThrow().balanceOf(COINS)).isEqualTo(coins(1000));
        assertThat(loans.findById(loan.id())).isPresent();
    }

    @Test
    void disburseRejectedByTheClampWritesNoLoanRow() {
        PlayerRef debtor = randomPlayer();
        // Seat the wallet near the max so the principal credit breaches the clamp.
        wallets.upsertBalance(debtor, Money.of(COINS, new BigDecimal("999999999999")));
        Loan loan = loanFor(debtor, 1000, 1100, 4);

        Result<Unit, TransferError> result = loans.disburse(loan);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);
        assertThat(loans.findById(loan.id())).isEmpty();
        assertThat(wallets.findByOwner(debtor).orElseThrow().balanceOf(COINS).amount())
                .isEqualByComparingTo(new BigDecimal("999999999999"));
    }

    @Test
    void applyRepaymentDebitsAndUpdatesTheLoanAtomically() {
        PlayerRef debtor = randomPlayer();
        Loan loan = loanFor(debtor, 1000, 1000, 4);
        loans.disburse(loan); // debtor now holds 1000

        Money installment = coins(250);
        Loan updated = loan.afterPayment(installment).withNextPayment(2_000L);

        Result<Unit, TransferError> result = loans.applyRepayment(debtor, installment, updated, updated.isFullyPaid());

        assertThat(result.isOk()).isTrue();
        assertThat(wallets.findByOwner(debtor).orElseThrow().balanceOf(COINS)).isEqualTo(coins(750));
        assertThat(loans.findById(loan.id()).orElseThrow().remainingAmount()).isEqualTo(coins(750));
    }

    @Test
    void applyRepaymentDeletesTheLoanWhenFullyPaid() {
        PlayerRef debtor = randomPlayer();
        Loan loan = loanFor(debtor, 1000, 1000, 1);
        loans.disburse(loan);

        Money settlement = loan.settlementFor(coins(1000));
        Loan updated = loan.afterPayment(settlement);

        assertThat(loans.applyRepayment(debtor, settlement, updated, updated.isFullyPaid())
                        .isOk())
                .isTrue();
        assertThat(loans.findById(loan.id())).isEmpty();
        assertThat(wallets.findByOwner(debtor).orElseThrow().balanceOf(COINS).amount())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyRepaymentShortOfFundsDebitsNothingAndLeavesTheLoanUntouched() {
        PlayerRef debtor = randomPlayer();
        Loan loan = loanFor(debtor, 100, 100, 4);
        loans.disburse(loan); // debtor holds only 100

        Money installment = coins(250); // more than the debtor holds
        Loan updated = loan.afterPayment(loan.settlementFor(installment)).withNextPayment(2_000L);

        Result<Unit, TransferError> result = loans.applyRepayment(debtor, installment, updated, updated.isFullyPaid());

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        // Money untouched and the loan row unchanged (still owes the original total).
        assertThat(wallets.findByOwner(debtor).orElseThrow().balanceOf(COINS)).isEqualTo(coins(100));
        assertThat(loans.findById(loan.id()).orElseThrow().remainingAmount()).isEqualTo(coins(100));
    }
}
