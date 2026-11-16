package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * The eco-admin surface, {@code /eco give|take|set|reset} per target and {@code /eco giveall|giverandom|resetall}
 * in bulk. Every verb is permission-gated at the command boundary and audited exactly once here (bulk verbs
 * with an affected-count, never per-target spam). The
 * single-sided {@code give}/{@code take} route through the {@link EconomyProvider} so they honour the
 * currency clamp and the offline-target upsert; {@code set}/{@code reset} target an exact balance, which only
 * the native ledger expresses, so they go through the {@link WalletRepository} the economy context owns.
 *
 * <p>A {@code set}/{@code reset} the store could not apply returns {@link TransferError#PLAYER_OFFLINE}, tells the
 * actor, and records no ledger row or audit entry.
 *
 * <p>{@code giveall} and {@code resetall} reach every account the ledger knows through the {@link AccountSweep}, not
 * the players one server happens to see. The sweep writes each account's ledger row in the same transaction as its
 * balance, so these two verbs never touch {@link TransactionHistory}. The actor hears the count only once the sweep
 * has committed. One sweep runs at a time on a server, so a second click on the bulk screen cannot pay everyone twice.
 *
 * <p>An offline target is first materialised via {@link WalletRepository#ensureOwner}, so a give/set to a
 * never-joined player never breaks a foreign key: every ledger row names an owner that exists.
 */
public final class EcoAdmin {

    private final EconomyProvider economy;
    private final WalletRepository repository;
    private final EconomyAudit audit;
    private final EconomyNotifier notifier;
    private final TransactionHistory history;
    private final AccountSweep sweep;
    private final Clock clock;
    private final AtomicBoolean sweeping = new AtomicBoolean();

    public EcoAdmin(
            EconomyProvider economy,
            WalletRepository repository,
            EconomyAudit audit,
            EconomyNotifier notifier,
            TransactionHistory history,
            AccountSweep sweep,
            Clock clock) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.history = Objects.requireNonNull(history, "history");
        this.sweep = Objects.requireNonNull(sweep, "sweep");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code /eco give <target> <amount> [currency]}: credit {@code target} by {@code amount}. */
    public Result<Unit, TransferError> give(PlayerRef actor, PlayerRef target, Money amount) {
        return single(actor, target, amount, true, EconomyReason.ADMIN_GIVE, EconomyMessageKey.ECO_ADMIN_GIVEN);
    }

    /** {@code /eco take <target> <amount> [currency]}: debit {@code target} by {@code amount}. */
    public Result<Unit, TransferError> take(PlayerRef actor, PlayerRef target, Money amount) {
        return single(actor, target, amount, false, EconomyReason.ADMIN_TAKE, EconomyMessageKey.ECO_ADMIN_TAKEN);
    }

    /** {@code /eco set <target> <amount> [currency]}: set {@code target}'s balance to exactly {@code amount}. */
    public Result<Unit, TransferError> set(PlayerRef actor, PlayerRef target, Money amount) {
        validate(actor, target, amount);
        repository.ensureOwner(target);
        Money before = economy.balance(target, amount.currency());
        if (!settle(target, amount)) {
            return notSet(actor, amount);
        }
        recordNetChange(target, before, amount, EconomyReason.ADMIN_SET);
        audit.adminMutation(actor, target, amount, EconomyReason.ADMIN_SET);
        notify(actor, target, amount, EconomyMessageKey.ECO_ADMIN_SET);
        return Result.ok();
    }

    /** {@code /eco reset <target> [currency]}: set {@code target}'s balance to the currency's starting figure. */
    public Result<Unit, TransferError> reset(PlayerRef actor, PlayerRef target, Currency currency) {
        Money starting = Money.of(currency, currency.starting());
        validate(actor, target, starting);
        repository.ensureOwner(target);
        Money before = economy.balance(target, currency);
        if (!settle(target, starting)) {
            return notSet(actor, starting);
        }
        recordNetChange(target, before, starting, EconomyReason.ADMIN_RESET);
        audit.adminMutation(actor, target, starting, EconomyReason.ADMIN_RESET);
        notify(actor, target, starting, EconomyMessageKey.ECO_ADMIN_RESET);
        return Result.ok();
    }

    /**
     * {@code /eco giveall <amount> [currency]}: credit every known account by {@code amount}. Returns the accounts
     * credited; an account the credit would push past the currency's {@code max} is left out, and the actor is told
     * how many were.
     */
    public int giveAll(PlayerRef actor, Money amount) {
        validateBulk(actor, amount);
        SweepOutcome outcome = runSweep(
                actor, amount.currency(), () -> sweep.creditEvery(amount, EconomyReason.ADMIN_GIVEALL, clock.millis()));
        if (outcome == null) {
            return 0;
        }
        audit.bulkMutation(actor, amount, outcome.changed(), EconomyReason.ADMIN_GIVEALL);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVEALL,
                Map.of("count", Integer.toString(outcome.changed()), "amount", notifier.amount(amount)));
        if (outcome.skipped() > 0) {
            notifier.send(
                    actor,
                    EconomyMessageKey.ECO_ADMIN_GIVEALL_CAPPED,
                    Map.of("count", Integer.toString(outcome.skipped())));
        }
        return outcome.changed();
    }

    /** {@code /eco giverandom <amount> [currency]}: credit {@code amount} to one chosen online player. */
    public Result<Unit, TransferError> giveRandom(PlayerRef actor, PlayerRef chosen, Money amount) {
        validate(actor, chosen, amount);
        repository.ensureOwner(chosen);
        Result<Unit, TransferError> result = economy.credit(chosen, amount);
        if (result.isErr()) {
            return result;
        }
        history.recordCredit(ownerId(chosen), amount, EconomyReason.ADMIN_GIVERANDOM, clock.millis());
        audit.bulkMutation(actor, amount, 1, EconomyReason.ADMIN_GIVERANDOM);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVERANDOM,
                Map.of("player", chosen.name(), "amount", notifier.amount(amount)));
        return Result.ok();
    }

    /** {@code /eco give-random <target> <min> <max> [currency]}: credit {@code target} by a random amount in range {@code [min, max]}. */
    public Result<Money, TransferError> giveRandomRange(PlayerRef actor, PlayerRef target, Money min, Money max) {
        validate(actor, target, min);
        Objects.requireNonNull(max, "max");
        if (!min.currency().equals(max.currency())) {
            throw new IllegalArgumentException("currencies must match");
        }
        if (min.amount().compareTo(max.amount()) > 0) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }

        Money amount = randomInRange(min, max);

        repository.ensureOwner(target);
        Result<Unit, TransferError> result = economy.credit(target, amount);
        if (result.isErr()) {
            return Result.err(result.errorOrThrow());
        }
        history.recordCredit(ownerId(target), amount, EconomyReason.ADMIN_GIVE, clock.millis());
        audit.adminMutation(actor, target, amount, EconomyReason.ADMIN_GIVE);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVEN,
                Map.of("player", target.name(), "amount", notifier.amount(amount)));
        return Result.ok(amount);
    }

    /**
     * {@code /eco resetall --confirm [currency]}: put every known account's balance back to the currency's starting
     * figure. Returns the accounts whose balance changed; one already at the starting figure is not counted.
     */
    public int resetAll(PlayerRef actor, Currency currency) {
        Money starting = Money.of(currency, currency.starting());
        validateBulk(actor, starting);
        SweepOutcome outcome = runSweep(
                actor, currency, () -> sweep.resetEvery(starting, EconomyReason.ADMIN_RESETALL, clock.millis()));
        if (outcome == null) {
            return 0;
        }
        audit.bulkMutation(actor, starting, outcome.changed(), EconomyReason.ADMIN_RESETALL);
        notifier.send(
                actor, EconomyMessageKey.ECO_ADMIN_RESETALL, Map.of("count", Integer.toString(outcome.changed())));
        return outcome.changed();
    }

    /**
     * Run one sweep of {@code currency}, or tell {@code actor} why none ran: the currency's balances are kept where
     * the ledger cannot list them, another sweep is still running here, or the sweep failed and its transaction was
     * rolled back. Null in each of those cases, when nothing changed and nothing may be announced or audited.
     */
    private @Nullable SweepOutcome runSweep(PlayerRef actor, Currency currency, Supplier<SweepOutcome> work) {
        if (!sweep.reaches(currency)) {
            notifier.send(
                    actor,
                    EconomyMessageKey.ECO_ADMIN_BULK_UNREACHABLE,
                    Map.of("currency", currency.id().value()));
            return null;
        }
        if (!sweeping.compareAndSet(false, true)) {
            notifier.send(actor, EconomyMessageKey.ECO_ADMIN_BULK_RUNNING, Map.of());
            return null;
        }
        try {
            return work.get();
        } catch (RuntimeException failure) {
            // The sweep is one transaction, so a failure rolled every account back. The actor must hear that, and the
            // failure still escapes so the async lane logs its cause.
            notifier.send(actor, EconomyMessageKey.ECO_ADMIN_BULK_FAILED, Map.of());
            throw failure;
        } finally {
            sweeping.set(false);
        }
    }

    /**
     * A random amount in {@code [min, max]}, computed in {@link java.math.BigDecimal} so no money figure ever
     * passes through a {@code double}. The only floating-point value is the {@code [0,1)} fraction the RNG
     * yields; it scales the exact decimal range, and the result is normalised to the currency's precision.
     */
    private static Money randomInRange(Money min, Money max) {
        Currency currency = min.currency();
        java.math.BigDecimal range = max.amount().subtract(min.amount());
        double fraction = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        java.math.BigDecimal offset = range.multiply(java.math.BigDecimal.valueOf(fraction));
        java.math.BigDecimal value =
                min.amount().add(offset).setScale(currency.precision(), java.math.RoundingMode.HALF_UP);
        return Money.of(currency, value);
    }

    private Result<Unit, TransferError> single(
            PlayerRef actor,
            PlayerRef target,
            Money amount,
            boolean credit,
            EconomyReason reason,
            EconomyMessageKey feedback) {
        validate(actor, target, amount);
        repository.ensureOwner(target);
        Result<Unit, TransferError> result = credit ? economy.credit(target, amount) : economy.debit(target, amount);
        if (result.isErr()) {
            notifier.send(actor, result.errorOrThrow().messageKey(), Map.of("amount", notifier.amount(amount)));
            return result;
        }
        if (credit) {
            history.recordCredit(ownerId(target), amount, reason, clock.millis());
        } else {
            history.recordDebit(ownerId(target), amount, reason, clock.millis());
        }
        audit.adminMutation(actor, target, amount, reason);
        notify(actor, target, amount, feedback);
        return Result.ok();
    }

    /**
     * Write an exact balance. False when the store could not apply it (a physical balance whose holder's thread
     * did not take the change); nothing changed then, so the caller must not record or announce it.
     */
    private boolean settle(PlayerRef target, Money balance) {
        try {
            repository.upsertBalance(target, balance);
            return true;
        } catch (IllegalStateException notApplied) {
            return false;
        }
    }

    private Result<Unit, TransferError> notSet(PlayerRef actor, Money amount) {
        TransferError error = TransferError.PLAYER_OFFLINE;
        notifier.send(actor, error.messageKey(), Map.of("amount", notifier.amount(amount)));
        return Result.err(error);
    }

    private static String ownerId(PlayerRef owner) {
        return owner.uuid().toString();
    }

    /**
     * Records the net change of an exact-balance write (set/reset) as a single credit or debit. A no-op write
     * (the new balance already matches the old) records nothing, so a zero-delta set never logs a phantom row.
     */
    private void recordNetChange(PlayerRef target, Money before, Money after, EconomyReason reason) {
        int sign = after.amount().compareTo(before.amount());
        if (sign == 0) {
            return;
        }
        Money delta = Money.of(
                after.currency(), after.amount().subtract(before.amount()).abs());
        if (sign > 0) {
            history.recordCredit(ownerId(target), delta, reason, clock.millis());
        } else {
            history.recordDebit(ownerId(target), delta, reason, clock.millis());
        }
    }

    private void notify(PlayerRef actor, PlayerRef target, Money amount, EconomyMessageKey feedback) {
        notifier.send(actor, feedback, Map.of("player", target.name(), "amount", notifier.amount(amount)));
    }

    private void validate(PlayerRef actor, PlayerRef target, Money amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
    }

    private void validateBulk(PlayerRef actor, Money amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(amount, "amount");
    }
}
