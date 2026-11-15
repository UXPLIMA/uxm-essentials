package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.economy.application.port.LoanRepository;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Loan.CreditScore;
import com.uxplima.uxmessentials.economy.domain.LoanError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.event.LoanDisbursed;
import com.uxplima.uxmessentials.economy.domain.event.LoanRepaid;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Orchestrates loan disbursement, repayment, and credit scoring. Every money move is one atomic call into the
 * {@link LoanRepository} (the wallet leg and the loan-row change commit together or not at all) so no
 * in-JVM lock is needed and a persistence failure can never create or lose money. The economics (limit,
 * interest band, installment cycle, score deltas) come from the injected {@link LoanPolicy}, not from
 * hardcoded constants.
 *
 * <p>Loans are a native-ledger feature: the atomic move runs through the wallet repository the economy context
 * owns. When the active provider is a foreign economy, the feature is refused with
 * {@link LoanError#PROVIDER_UNSUPPORTED} rather than attempting a non-atomic move through that provider.
 */
public final class LoanService {

    private final LoanRepository loanRepository;
    private final LoanPolicy policy;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final boolean nativeLedger;

    public LoanService(
            LoanRepository loanRepository,
            LoanPolicy policy,
            DomainEventPublisher events,
            Clock clock,
            boolean nativeLedger) {
        this.loanRepository = Objects.requireNonNull(loanRepository, "loanRepository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nativeLedger = nativeLedger;
    }

    /** Distributes a loan to a debtor player. Dynamic limits are enforced based on credit score. */
    public Result<Loan, LoanError> takeLoan(PlayerRef debtor, Money principal, int installments) {
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(principal, "principal");
        if (!nativeLedger) {
            return Result.err(LoanError.PROVIDER_UNSUPPORTED);
        }
        if (installments <= 0 || principal.isNegative() || principal.isZero()) {
            return Result.err(LoanError.INVALID_TERMS);
        }

        CreditScore creditScore = loanRepository.getCreditScore(debtor);
        if (principal.amount().compareTo(policy.limitFor(creditScore.score())) > 0) {
            return Result.err(LoanError.LIMIT_EXCEEDED);
        }

        Loan loan = buildLoan(debtor, principal, installments, creditScore.score());
        if (loan.installmentAmount().isZero()) {
            // An installment that rounds to nothing at the currency's precision could never be collected.
            return Result.err(LoanError.INVALID_TERMS);
        }
        Result<Unit, TransferError> disbursed = loanRepository.disburse(loan);
        if (disbursed.isErr()) {
            return Result.err(toLoanError(disbursed.errorOrThrow()));
        }
        events.publish(new LoanDisbursed(loan.id(), debtor, principal));
        return Result.ok(loan);
    }

    /**
     * Pays {@code amount} towards a loan. Increases credit score on successful payment. A zero or negative amount
     * is refused with {@link LoanError#INVALID_TERMS}: it is not a payment, and the domain would throw on it.
     */
    public Result<Unit, LoanError> payInstallment(PlayerRef debtor, String loanId, Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative() || amount.isZero()) {
            return Result.err(LoanError.INVALID_TERMS);
        }
        return pay(debtor, loanId, loan -> amount).map(settled -> Unit.INSTANCE);
    }

    /**
     * Pays the loan's next installment, the figure automatic repayment would collect, and returns what was settled.
     * Increases credit score on successful payment.
     */
    public Result<Money, LoanError> payInstallment(PlayerRef debtor, String loanId) {
        return pay(debtor, loanId, LoanService::dueInstallment);
    }

    private Result<Money, LoanError> pay(PlayerRef debtor, String loanId, Function<Loan, Money> offer) {
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(loanId, "loanId");
        if (!nativeLedger) {
            return Result.err(LoanError.PROVIDER_UNSUPPORTED);
        }

        Optional<Loan> found = loanRepository.findById(loanId);
        if (found.isEmpty()) {
            return Result.err(LoanError.NOT_FOUND);
        }
        Loan loan = found.get();
        if (!loan.debtor().uuid().equals(debtor.uuid())) {
            return Result.err(LoanError.WRONG_DEBTOR);
        }

        // The settlement never exceeds the residual. The final installment clears the exact remainder, and an
        // over-offer is trimmed here so the debtor is never debited more than they owe.
        Money settlement = loan.settlementFor(offer.apply(loan));
        Loan updated = loan.afterPayment(settlement).withNextPayment(nextCycle());
        Result<Unit, TransferError> applied =
                loanRepository.applyRepayment(debtor, settlement, updated, updated.isFullyPaid());
        if (applied.isErr()) {
            return Result.err(toLoanError(applied.errorOrThrow()));
        }
        bumpScore(debtor, policy.onTimeManualDelta());
        events.publish(new LoanRepaid(loan.id(), debtor, settlement, updated.remainingAmount()));
        return Result.ok(settlement);
    }

    /**
     * What one installment of {@code loan} collects. A stored installment that rounds to nothing is not a payment,
     * so the remainder is collected instead: it closes the loan, where a zero settlement would throw.
     */
    private static Money dueInstallment(Loan loan) {
        return loan.installmentAmount().isZero() ? loan.remainingAmount() : loan.installmentAmount();
    }

    /** Background scheduler routine to automatically repay due loans. Reduces credit score on failure. */
    public void processAutoRepayments() {
        List<Loan> activeLoans = loanRepository.findAllActive();
        long now = clock.millis();
        for (Loan loan : activeLoans) {
            if (now >= loan.nextPaymentAt()) {
                attemptAutoRepayment(loan);
            }
        }
    }

    private void attemptAutoRepayment(Loan loan) {
        PlayerRef debtor = loan.debtor();
        // dueInstallment never offers zero, which would throw and stop every loan after this one in the run.
        Money settlement = loan.settlementFor(dueInstallment(loan));
        Loan updated = loan.afterPayment(settlement).withNextPayment(nextCycle());
        Result<Unit, TransferError> applied =
                loanRepository.applyRepayment(debtor, settlement, updated, updated.isFullyPaid());
        if (applied.isOk()) {
            bumpScore(debtor, policy.onTimeAutoDelta());
            events.publish(new LoanRepaid(loan.id(), debtor, settlement, updated.remainingAmount()));
        } else {
            // Missed payment: penalise the score and push the cycle so it retries next interval.
            bumpScore(debtor, policy.missedDelta());
            loanRepository.save(loan.withNextPayment(nextCycle()));
        }
    }

    /** Lists all active loan IDs a player has. */
    public List<String> getActiveLoanIds(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return loanRepository.findByDebtor(player).stream().map(Loan::id).toList();
    }

    /** Gets all active loans a player has. */
    public List<Loan> getActiveLoans(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return loanRepository.findByDebtor(player);
    }

    /** Gets the credit score rating for a player. */
    public CreditScore getCreditScore(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return loanRepository.getCreditScore(player);
    }

    /**
     * The read-only loan terms a debtor of {@code creditScore} qualifies for, computed from the same
     * {@link LoanPolicy} {@link #takeLoan} and {@link #buildLoan} use. The dashboard renders these figures so
     * the displayed limit and interest never disagree with what a loan actually charges once an operator tunes
     * the policy: the GUI does no loan math of its own.
     */
    public LoanQuote quote(int creditScore) {
        return new LoanQuote(policy.limitFor(creditScore), policy.interestFor(creditScore));
    }

    /**
     * A debtor's qualifying loan terms at a given credit score: the maximum principal and the annual-style
     * interest rate (a fraction, e.g. {@code 0.22} for 22%). Derived from the {@link LoanPolicy}, so a view
     * that renders it shows exactly the economics a real loan uses.
     */
    public record LoanQuote(BigDecimal limit, BigDecimal interestRate) {
        public LoanQuote {
            Objects.requireNonNull(limit, "limit");
            Objects.requireNonNull(interestRate, "interestRate");
        }
    }

    private Loan buildLoan(PlayerRef debtor, Money principal, int installments, int creditScore) {
        BigDecimal interestRate = policy.interestFor(creditScore);
        BigDecimal totalRepayable = principal.amount().multiply(BigDecimal.ONE.add(interestRate));
        BigDecimal installmentAmt = totalRepayable.divide(new BigDecimal(installments), 4, RoundingMode.HALF_UP);
        Money installment = Money.of(principal.currency(), installmentAmt);
        long now = clock.millis();
        return Loan.open(
                debtor,
                principal,
                Money.of(principal.currency(), totalRepayable),
                interestRate,
                installments,
                installment,
                now,
                nextCycle());
    }

    private long nextCycle() {
        return clock.millis() + policy.installmentCycle().toMillis();
    }

    private void bumpScore(PlayerRef debtor, int delta) {
        CreditScore current = loanRepository.getCreditScore(debtor);
        int next = Math.max(policy.scoreFloor(), Math.min(policy.scoreCeiling(), current.score() + delta));
        loanRepository.saveCreditScore(new CreditScore(debtor, next, clock.millis()));
    }

    private static LoanError toLoanError(TransferError error) {
        // A disburse rejected by the wallet's max-balance clamp, or a repayment short of funds, both surface as
        // the closest loan-facing failure; INVALID_TERMS covers a disburse that cannot land (e.g. cap reached).
        return switch (error) {
            case INSUFFICIENT_FUNDS -> LoanError.INSUFFICIENT_FUNDS;
            case BALANCE_MAX_EXCEEDED -> LoanError.INVALID_TERMS;
            default -> LoanError.INVALID_TERMS;
        };
    }
}
