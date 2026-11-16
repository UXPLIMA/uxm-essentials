package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.SweepState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Changes one currency's balance on every account the ledger knows: online or offline, on this backend or on any
 * other sharing the database. The bulk eco-admin verbs go through here, because a roster of the players online on
 * one server is not "every account".
 *
 * <p>A sweep is recorded in a journal when it is {@link #open opened} and then {@link #advance advanced} a chunk of
 * accounts at a time. Each chunk is one short transaction that changes its balances, writes one ledger row per
 * changed account and moves the journal's cursor past the chunk, all together or not at all. A payment therefore
 * waits on a chunk's row locks for milliseconds, never for the whole sweep, and a sweep cut short by a stop or a lost
 * connection carries on from its cursor without changing any account twice. A runner locks the journal row before
 * each chunk, so two runners of one sweep (a server resuming it while another still runs it) take turns. Every
 * balance write is made against the stored row under the database's row lock, so a racing payment is never lost.
 *
 * <p>A currency has at most one unfinished sweep, on every server sharing the database: the journal refuses to open a
 * second while one is running or paused, so a command repeated after a pause or a crash cannot pay everyone twice,
 * and a giveall and a resetall of one currency never interleave.
 *
 * <p>Only a currency whose balances are rows of this ledger can be swept. A currency kept by another economy plugin,
 * or held as items in inventories, has accounts this plugin cannot list, so {@link #reaches} says no and the caller
 * must refuse rather than change some of them.
 *
 * <p>Every method is database I/O and runs off the tick and region threads.
 */
public interface AccountSweep {

    /** Whether every balance in {@code currency} lives in the ledger this sweep writes. */
    boolean reaches(Currency currency);

    /**
     * Record a new sweep in the journal, with nothing changed yet, unless {@code amount}'s currency already has an
     * unfinished one. A {@link SweepKind#CREDIT} adds {@code amount} (opening the balance of an account that never held
     * the currency) and leaves out an account it would push past the currency's {@code max}; a {@link SweepKind#RESET}
     * sets every balance to {@code amount}, and an account already there is not changed.
     */
    Opening open(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at);

    /**
     * Change the next chunk of {@code sweep}'s accounts in one transaction, or mark the sweep done when none is left.
     * When the database refuses the chunk's rows for good (a figure a column cannot hold, a constraint), the sweep is
     * marked {@link SweepState#FAILED} and the step says {@link Progress#FAILED}. A failure that may pass (a lost
     * connection) is thrown, and the chunk changed nothing.
     */
    Step advance(Sweep sweep);

    /** Record that {@code sweep} is waiting to be tried again after {@code error}. Best effort, like any write. */
    void pause(Sweep sweep, String error);

    /**
     * The sweeps to carry on, running or paused, in the order they were opened. One whose currency is no longer
     * configured cannot be carried on and is marked {@link SweepState#FAILED} instead of being returned.
     */
    List<Sweep> resumable();

    /** Every sweep still running or paused, as an operator reads them. */
    List<SweepStatus> unfinished();

    /**
     * Stop the unfinished sweep whose id is {@code id} or starts with it, keeping what its chunks already changed.
     * Empty when no unfinished sweep, or more than one, matches.
     */
    Optional<SweepStatus> cancel(String id, long at);

    /** A sweep that reaches no currency, for a server whose balances are kept by another economy plugin. */
    static AccountSweep unreachable() {
        return new AccountSweep() {
            @Override
            public boolean reaches(Currency currency) {
                return false;
            }

            @Override
            public Opening open(
                    SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at) {
                throw new IllegalStateException("no currency is kept in this plugin's ledger");
            }

            @Override
            public Step advance(Sweep sweep) {
                throw new IllegalStateException("no currency is kept in this plugin's ledger");
            }

            @Override
            public void pause(Sweep sweep, String error) {}

            @Override
            public List<Sweep> resumable() {
                return List.of();
            }

            @Override
            public List<SweepStatus> unfinished() {
                return List.of();
            }

            @Override
            public Optional<SweepStatus> cancel(String id, long at) {
                return Optional.empty();
            }
        };
    }

    /** What {@link #open} did: opened a sweep, or found the currency's unfinished one. */
    sealed interface Opening permits Opened, Busy {}

    /** The new sweep, journalled and ready to advance. */
    record Opened(Sweep sweep) implements Opening {

        public Opened {
            Objects.requireNonNull(sweep, "sweep");
        }
    }

    /** Nothing was opened: {@code existing} has not finished. */
    record Busy(SweepStatus existing) implements Opening {

        public Busy {
            Objects.requireNonNull(existing, "existing");
        }
    }

    /**
     * One sweep as the journal holds it.
     *
     * @param id the journal's id for it
     * @param amount the amount added, or the balance set
     * @param actor who asked for it
     * @param startedAt when it was opened, the time every one of its ledger rows carries
     */
    record Sweep(
            String id,
            SweepKind kind,
            SweepScope scope,
            Money amount,
            EconomyReason reason,
            PlayerRef actor,
            long startedAt) {

        public Sweep {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(actor, "actor");
        }
    }

    /**
     * A journal row as an operator reads it. The currency is its configured id, which may no longer be configured.
     *
     * @param amount the amount added, or the balance set
     * @param total what its chunks have done so far
     * @param lastError why its last chunk failed, or null
     */
    record SweepStatus(
            String id,
            SweepKind kind,
            SweepScope scope,
            String currency,
            BigDecimal amount,
            EconomyReason reason,
            PlayerRef actor,
            SweepState state,
            SweepOutcome total,
            long startedAt,
            @Nullable String lastError) {

        public SweepStatus {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(total, "total");
        }
    }

    /**
     * What one call to {@link #advance} did.
     *
     * @param total the sweep's totals so far, across every runner
     * @param changedOwners the accounts this chunk changed, whose cached balances are now stale
     * @param progress whether more is left, and who ended the sweep
     * @param error why the sweep failed, for {@link Progress#FAILED}; else null
     */
    record Step(
            SweepOutcome total,
            List<UUID> changedOwners,
            Progress progress,
            @Nullable String error) {

        public Step {
            Objects.requireNonNull(total, "total");
            changedOwners = List.copyOf(changedOwners);
            Objects.requireNonNull(progress, "progress");
        }

        public Step(SweepOutcome total, List<UUID> changedOwners, Progress progress) {
            this(total, changedOwners, progress, null);
        }
    }

    /** Where a sweep stands after a step. */
    enum Progress {
        /** A chunk was done and more accounts are left. */
        MORE,
        /** This step found no account left and marked the sweep done: its caller announces the result. */
        FINISHED,
        /** The sweep had already ended (done by another runner, cancelled, or failed); nothing was changed. */
        ENDED,
        /** This step's chunk was refused for good and the sweep marked failed; nothing of the chunk was changed. */
        FAILED
    }

    /**
     * What a sweep did.
     *
     * @param changed the accounts whose balance changed, each with one ledger row
     * @param skipped the accounts left as they were because the change would break the currency's limits
     * @param gone the accounts removed (a wallet purge) between being listed and being changed
     */
    record SweepOutcome(int changed, int skipped, int gone) {

        public SweepOutcome {
            if (changed < 0 || skipped < 0 || gone < 0) {
                throw new IllegalArgumentException(
                        "counts must not be negative: " + changed + ", " + skipped + ", " + gone);
            }
        }

        /** Nothing done yet. */
        public static SweepOutcome none() {
            return new SweepOutcome(0, 0, 0);
        }

        /** This outcome and {@code other} added together. */
        public SweepOutcome plus(SweepOutcome other) {
            Objects.requireNonNull(other, "other");
            return new SweepOutcome(changed + other.changed, skipped + other.skipped, gone + other.gone);
        }
    }
}
