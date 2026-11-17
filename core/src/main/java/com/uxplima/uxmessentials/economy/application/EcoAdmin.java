package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepStatus;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

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
 * <p>{@code giveall} and {@code resetall} reach every player, not the players one server happens to see, wherever the
 * currency's money is kept: the {@link SweepRunner} runs them chunk by chunk and tells the actor the count once the
 * last chunk is stored. {@code giveall --online} is the one bulk verb that reads the roster of this server.
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
    private final SweepRunner sweeps;
    private final Clock clock;

    public EcoAdmin(
            EconomyProvider economy,
            WalletRepository repository,
            EconomyAudit audit,
            EconomyNotifier notifier,
            TransactionHistory history,
            SweepRunner sweeps,
            Clock clock) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.history = Objects.requireNonNull(history, "history");
        this.sweeps = Objects.requireNonNull(sweeps, "sweeps");
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
     * {@code /eco giveall <amount> [currency] [--all-accounts]}: credit every account in {@code scope} by
     * {@code amount}. Returns the accounts credited when the sweep finished here, else 0; an account the credit would
     * push past the currency's {@code max} is left out, and the actor is told how many were.
     */
    public int giveAll(PlayerRef actor, Money amount, SweepScope scope) {
        validateBulk(actor, amount);
        return sweep(actor, SweepKind.CREDIT, scope, amount, EconomyReason.ADMIN_GIVEALL);
    }

    /**
     * {@code /eco giveall <amount> [currency] --online}: credit each of {@code online}, the players online on the
     * issuing server, one by one, the way a scheduled reward for being online expects. Returns how many were credited.
     * A currency whose balance changes only while its player is online (items, experience) is queued per player and
     * handed over at once, so what does not fit an inventory waits for the next join instead of being dropped.
     */
    public int giveOnline(PlayerRef actor, List<PlayerRef> online, Money amount) {
        validateBulk(actor, amount);
        DeliverSweptItems.OnlineGive given = sweeps.deferred(amount.currency())
                ? sweeps.giveOnline(List.copyOf(online), amount, EconomyReason.ADMIN_GIVEALL)
                : new DeliverSweptItems.OnlineGive(creditEach(online, amount), 0);
        int affected = given.players();
        audit.bulkMutation(actor, amount, affected, EconomyReason.ADMIN_GIVEALL);
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_GIVEALL_ONLINE,
                Map.of("count", Integer.toString(affected), "amount", notifier.amount(amount)));
        if (given.heldBack() > 0) {
            notifier.send(
                    actor,
                    EconomyMessageKey.ECO_ADMIN_GIVEALL_ONLINE_HELD,
                    Map.of("count", Integer.toString(given.heldBack())));
        }
        return affected;
    }

    private int creditEach(List<PlayerRef> online, Money amount) {
        int affected = 0;
        for (PlayerRef target : List.copyOf(online)) {
            repository.ensureOwner(target);
            if (economy.credit(target, amount).isOk()) {
                history.recordCredit(ownerId(target), amount, EconomyReason.ADMIN_GIVEALL, clock.millis());
                affected++;
            }
        }
        return affected;
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
     * {@code /eco resetall --confirm [currency] [--all-accounts]}: put every account in {@code scope} back to the
     * currency's starting figure. Returns the accounts whose balance changed when the sweep finished here, else 0; one
     * already at the starting figure is not counted.
     */
    public int resetAll(PlayerRef actor, Currency currency, SweepScope scope) {
        Money starting = Money.of(currency, currency.starting());
        validateBulk(actor, starting);
        return sweep(actor, SweepKind.RESET, scope, starting, EconomyReason.ADMIN_RESETALL);
    }

    /**
     * Carry on every sweep over every account a stop left running, on any server. Called once when the economy
     * starts, off the tick.
     */
    public void resumeSweeps() {
        sweeps.resumeUnfinished();
    }

    /** {@code /eco sweeps}: tell {@code actor} every unfinished sweep, how far it got and why it last stopped. */
    public void listSweeps(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        List<SweepStatus> unfinished = sweeps.unfinished();
        if (unfinished.isEmpty()) {
            notifier.send(actor, EconomyMessageKey.ECO_ADMIN_SWEEPS_NONE, Map.of());
            return;
        }
        notifier.send(
                actor, EconomyMessageKey.ECO_ADMIN_SWEEPS_HEADER, Map.of("count", Integer.toString(unfinished.size())));
        for (SweepStatus status : unfinished) {
            Map<String, String> values = new HashMap<>();
            values.put("id", SweepRunner.shortId(status.id()));
            values.put("kind", status.kind() == SweepKind.CREDIT ? "giveall" : "resetall");
            values.put("amount", amount(status));
            values.put("currency", status.currency());
            values.put("scope", status.scope().name().toLowerCase(Locale.ROOT));
            values.put("state", status.state().name().toLowerCase(Locale.ROOT));
            values.put("count", Integer.toString(status.total().changed()));
            values.put("actor", status.actor().name());
            notifier.send(actor, EconomyMessageKey.ECO_ADMIN_SWEEPS_ENTRY, values);
            String error = status.lastError();
            if (error != null) {
                notifier.send(actor, EconomyMessageKey.ECO_ADMIN_SWEEPS_ERROR, Map.of("error", error));
            }
        }
    }

    /**
     * {@code /eco sweeps cancel <id>}: stop an unfinished sweep. The accounts it already changed keep the change, and
     * the audit gets one line with their count, as for a sweep that ran to its end.
     */
    public boolean cancelSweep(PlayerRef actor, String id) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(id, "id");
        Optional<SweepStatus> cancelled = sweeps.cancel(actor, id);
        if (cancelled.isEmpty()) {
            notifier.send(actor, EconomyMessageKey.ECO_ADMIN_SWEEP_UNKNOWN, Map.of("id", id));
            return false;
        }
        SweepStatus status = cancelled.get();
        currency(status.currency())
                .ifPresent(currency -> audit.bulkMutation(
                        status.actor(),
                        Money.of(currency, status.amount()),
                        status.total().changed(),
                        status.reason()));
        notifier.send(
                actor,
                EconomyMessageKey.ECO_ADMIN_SWEEP_CANCELLED,
                Map.of(
                        "id",
                        SweepRunner.shortId(status.id()),
                        "count",
                        Integer.toString(status.total().changed())));
        return true;
    }

    private Optional<Currency> currency(String id) {
        return economy.currencies().stream()
                .filter(currency -> currency.id().value().equals(id))
                .findFirst();
    }

    /** The sweep's amount as money, or as a bare figure when its currency is no longer configured. */
    private String amount(SweepStatus status) {
        return currency(status.currency())
                .map(currency -> notifier.amount(Money.of(currency, status.amount())))
                .orElseGet(() -> status.amount().stripTrailingZeros().toPlainString());
    }

    /** Run one sweep; the runner tells the actor the count, a sweep already unfinished, or a pause. */
    private int sweep(PlayerRef actor, SweepKind kind, SweepScope scope, Money amount, EconomyReason reason) {
        Objects.requireNonNull(scope, "scope");
        SweepOutcome outcome = sweeps.start(kind, scope, amount, reason, actor);
        return outcome == null ? 0 : outcome.changed();
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
