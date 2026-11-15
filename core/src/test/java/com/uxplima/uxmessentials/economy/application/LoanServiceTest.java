package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.LoanRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Loan.CreditScore;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * A loan pays its principal out once, is repaid by installment until the exact amount owed is collected, and a
 * missed automatic installment costs credit score and is tried again next cycle.
 */
class LoanServiceTest {

    private static final Currency DOLLARS =
            Currency.builder(CurrencyId.of("dollars")).build();
    private static final Duration CYCLE = Duration.ofHours(24);
    /** A stored loan whose installment rounds to nothing: 0.49 owed, 0.00 per installment. */
    private static final Loan STUCK = new Loan(
            "stuck",
            SAM,
            Money.of(DOLLARS, new BigDecimal("0.40")),
            Money.of(DOLLARS, new BigDecimal("0.49")),
            new BigDecimal("0.22"),
            100,
            Money.zero(DOLLARS),
            NOW.toEpochMilli(),
            NOW.toEpochMilli());

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryBank bank = new InMemoryBank(DOLLARS);
    private final Loans loans = new Loans();
    private final LoanService service =
            new LoanService(loans, LoanPolicy.defaults(), new RecordingEvents(), clock, true);

    private static Money dollars(String amount) {
        return Money.of(DOLLARS, new BigDecimal(amount));
    }

    private BigDecimal wallet(PlayerRef who) {
        return bank.balance(who, DOLLARS).amount();
    }

    private Loan stored(Loan loan) {
        return loans.findById(loan.id()).orElseThrow();
    }

    private Loan take(String principal, int installments) {
        return service.takeLoan(ALEX, dollars(principal), installments).orElseThrow();
    }

    @Test
    void aLoanPaysOutThePrincipalOnceAndOwesItWithTheInterestOfTheScore() {
        Loan loan = take("1000", 10);

        assertThat(wallet(ALEX)).isEqualByComparingTo("1000");
        // The lowest score borrows at the top of the band, 22%.
        assertThat(loan.remainingAmount().amount()).isEqualByComparingTo("1220");
        assertThat(loan.installmentAmount().amount()).isEqualByComparingTo("122");
        assertThat(loans.byId).containsOnlyKeys(loan.id());
    }

    @Test
    void aBetterScoreBorrowsMoreAndCheaper() {
        loans.scores.put(ALEX.uuid(), 1000);

        Loan loan = take("500000", 5);

        assertThat(loan.interestRate()).isEqualByComparingTo("0.02");
        assertThat(service.quote(100).limit()).isEqualByComparingTo("100000");
    }

    @Test
    void aPrincipalOverTheScoreLimitIsRefusedAndPaysNothing() {
        Result<Loan, LoanError> result = service.takeLoan(ALEX, dollars("100000.01"), 10);

        assertThat(result.errorOrThrow()).isEqualTo(LoanError.LIMIT_EXCEEDED);
        assertThat(wallet(ALEX)).isZero();
        assertThat(loans.byId).isEmpty();
    }

    @Test
    void termsThatCannotBeRepaidArePaidOutNever() {
        assertThat(service.takeLoan(ALEX, dollars("100"), 0).errorOrThrow()).isEqualTo(LoanError.INVALID_TERMS);
        assertThat(service.takeLoan(ALEX, dollars("0"), 5).errorOrThrow()).isEqualTo(LoanError.INVALID_TERMS);
        // 0.40 owed back over 100 installments is 0.00488 each, nothing at two decimals.
        assertThat(service.takeLoan(ALEX, dollars("0.40"), 100).errorOrThrow()).isEqualTo(LoanError.INVALID_TERMS);
        assertThat(new LoanService(loans, LoanPolicy.defaults(), new RecordingEvents(), clock, false)
                        .takeLoan(ALEX, dollars("100"), 5)
                        .errorOrThrow())
                .isEqualTo(LoanError.PROVIDER_UNSUPPORTED);
        assertThat(wallet(ALEX)).isZero();
        assertThat(loans.byId).isEmpty();
    }

    @Test
    void anInstallmentTakesTheMoneyLowersWhatIsOwedAndRaisesTheScore() {
        Loan loan = take("1000", 10);

        assertThat(service.payInstallment(ALEX, loan.id(), dollars("122")).isOk())
                .isTrue();

        assertThat(wallet(ALEX)).isEqualByComparingTo("878");
        assertThat(stored(loan).remainingAmount().amount()).isEqualByComparingTo("1098");
        assertThat(stored(loan).remainingInstallments()).isEqualTo(9);
        assertThat(loans.scores.get(ALEX.uuid())).isEqualTo(125);
    }

    @Test
    void payingMoreThanIsOwedTakesOnlyTheRemainderAndClosesTheLoan() {
        Loan loan = take("1000", 10);
        bank.set(ALEX, dollars("5000"));

        assertThat(service.payInstallment(ALEX, loan.id(), dollars("4000")).isOk())
                .isTrue();

        assertThat(wallet(ALEX)).isEqualByComparingTo("3780");
        assertThat(loans.byId).isEmpty();
        assertThat(service.payInstallment(ALEX, loan.id(), dollars("1")).errorOrThrow())
                .isEqualTo(LoanError.NOT_FOUND);
        assertThat(wallet(ALEX)).isEqualByComparingTo("3780");
    }

    @Test
    void onlyTheDebtorCanPayAndAnEmptyWalletChangesNothing() {
        Loan loan = take("1000", 10);
        bank.set(SAM, dollars("500"));
        bank.set(ALEX, dollars("50"));

        assertThat(service.payInstallment(SAM, loan.id(), dollars("122")).errorOrThrow())
                .isEqualTo(LoanError.WRONG_DEBTOR);
        assertThat(service.payInstallment(ALEX, loan.id(), dollars("122")).errorOrThrow())
                .isEqualTo(LoanError.INSUFFICIENT_FUNDS);

        assertThat(wallet(SAM)).isEqualByComparingTo("500");
        assertThat(wallet(ALEX)).isEqualByComparingTo("50");
        assertThat(stored(loan).remainingAmount().amount()).isEqualByComparingTo("1220");
        assertThat(loans.scores).doesNotContainKey(ALEX.uuid());
    }

    @Test
    void payingTheNextInstallmentTakesTheInstallmentAndAnswersWhatItSettled() {
        Loan loan = take("1000", 10);

        Result<Money, LoanError> paid = service.payInstallment(ALEX, loan.id());

        assertThat(paid.orElseThrow().amount()).isEqualByComparingTo("122");
        assertThat(wallet(ALEX)).isEqualByComparingTo("878");
        assertThat(stored(loan).remainingAmount().amount()).isEqualByComparingTo("1098");
    }

    @Test
    void theNextInstallmentOfAStoredLoanThatRoundsToNothingCollectsTheRemainder() {
        loans.byId.put(STUCK.id(), STUCK);
        bank.set(SAM, dollars("1"));

        Result<Money, LoanError> paid = service.payInstallment(SAM, STUCK.id());

        assertThat(paid.orElseThrow().amount()).isEqualByComparingTo("0.49");
        assertThat(wallet(SAM)).isEqualByComparingTo("0.51");
        assertThat(loans.byId).isEmpty();
    }

    @Test
    void aZeroOrNegativePaymentIsRefusedAndTakesNothing() {
        Loan loan = take("1000", 10);

        assertThat(service.payInstallment(ALEX, loan.id(), dollars("0")).errorOrThrow())
                .isEqualTo(LoanError.INVALID_TERMS);
        assertThat(service.payInstallment(ALEX, loan.id(), dollars("-5")).errorOrThrow())
                .isEqualTo(LoanError.INVALID_TERMS);

        assertThat(wallet(ALEX)).isEqualByComparingTo("1000");
        assertThat(stored(loan).remainingAmount().amount()).isEqualByComparingTo("1220");
        assertThat(loans.scores).doesNotContainKey(ALEX.uuid());
    }

    @Test
    void anAutomaticInstallmentIsCollectedOnlyOnceItFallsDue() {
        Loan loan = take("1000", 10);

        clock.advance(CYCLE.minusSeconds(1));
        service.processAutoRepayments();
        assertThat(wallet(ALEX)).isEqualByComparingTo("1000");

        clock.advance(Duration.ofSeconds(1));
        service.processAutoRepayments();
        service.processAutoRepayments();

        assertThat(wallet(ALEX)).isEqualByComparingTo("878");
        Loan after = stored(loan);
        assertThat(after.remainingAmount().amount()).isEqualByComparingTo("1098");
        assertThat(after.nextPaymentAt()).isEqualTo(clock.millis() + CYCLE.toMillis());
        assertThat(loans.scores.get(ALEX.uuid())).isEqualTo(110);
    }

    @Test
    void aMissedAutomaticInstallmentCostsScoreAndIsTriedAgainNextCycle() {
        loans.scores.put(ALEX.uuid(), 400);
        Loan loan = take("1000", 10);
        bank.set(ALEX, dollars("0"));

        clock.advance(CYCLE);
        service.processAutoRepayments();

        Loan missed = stored(loan);
        assertThat(missed.remainingAmount().amount())
                .isEqualTo(loan.remainingAmount().amount());
        assertThat(missed.nextPaymentAt()).isEqualTo(clock.millis() + CYCLE.toMillis());
        assertThat(loans.scores.get(ALEX.uuid())).isEqualTo(350);

        bank.set(ALEX, dollars("1000"));
        clock.advance(CYCLE);
        service.processAutoRepayments();

        assertThat(wallet(ALEX))
                .isEqualByComparingTo(
                        new BigDecimal("1000").subtract(loan.installmentAmount().amount()));
    }

    @Test
    void theScoreNeverFallsBelowTheFloor() {
        Loan loan = take("1000", 10);
        bank.set(ALEX, dollars("0"));

        clock.advance(CYCLE);
        service.processAutoRepayments();

        assertThat(loans.byId).containsKey(loan.id());
        assertThat(loans.scores.get(ALEX.uuid())).isEqualTo(100);
    }

    @Test
    void aStoredLoanWhoseInstallmentRoundsToNothingDoesNotStopTheOthersBeingCollected() {
        loans.byId.put(STUCK.id(), STUCK);
        bank.set(SAM, dollars("1"));
        Loan loan = take("1000", 10);

        clock.advance(CYCLE);
        service.processAutoRepayments();

        assertThat(wallet(ALEX)).isEqualByComparingTo("878");
        assertThat(wallet(SAM)).isEqualByComparingTo("0.51");
        assertThat(loans.byId).containsOnlyKeys(loan.id());
    }

    /** Loan rows and credit scores whose money legs run against the wallets in the same step, or not at all. */
    private final class Loans implements LoanRepository {

        private final Map<String, Loan> byId = new LinkedHashMap<>();
        private final Map<UUID, Integer> scores = new HashMap<>();

        @Override
        public Optional<Loan> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Loan> findByDebtor(PlayerRef debtor) {
            return byId.values().stream().filter(l -> l.debtor().equals(debtor)).toList();
        }

        @Override
        public List<Loan> findAllActive() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public void save(Loan loan) {
            byId.put(loan.id(), loan);
        }

        @Override
        public void delete(String id) {
            byId.remove(id);
        }

        @Override
        public Result<Unit, TransferError> disburse(Loan loan) {
            Result<Unit, TransferError> credited = bank.credit(loan.debtor(), loan.principal());
            if (credited.isOk()) {
                byId.put(loan.id(), loan);
            }
            return credited;
        }

        @Override
        public Result<Unit, TransferError> applyRepayment(
                PlayerRef debtor, Money paid, Loan updatedLoan, boolean fullyPaid) {
            Result<Unit, TransferError> debited = bank.debit(debtor, paid);
            if (debited.isErr()) {
                return debited;
            }
            if (fullyPaid) {
                byId.remove(updatedLoan.id());
            } else {
                byId.put(updatedLoan.id(), updatedLoan);
            }
            return debited;
        }

        @Override
        public CreditScore getCreditScore(PlayerRef player) {
            return new CreditScore(player, scores.getOrDefault(player.uuid(), 100), clock.millis());
        }

        @Override
        public void saveCreditScore(CreditScore creditScore) {
            scores.put(creditScore.player().uuid(), creditScore.score());
        }
    }
}
