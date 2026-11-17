package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepMode;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.SweepState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The journal of the changes to every balance of one currency ({@code /eco giveall}, {@code /eco resetall}): online or
 * offline, on this backend or on any other sharing the database, wherever the currency's money is kept.
 *
 * <p>A sweep is recorded when it is {@link #open opened} and then {@link #advance advanced} a chunk at a time, each
 * chunk one short transaction that moves the journal's cursor past it. What a chunk does depends on the sweep's
 * {@link SweepMode}:
 *
 * <ul>
 *   <li>{@link SweepMode#LEDGER}: changes the chunk's balances and writes one ledger row per changed account in the
 *       same transaction, so a sweep cut short carries on from its cursor without changing any account twice.
 *   <li>{@link SweepMode#DEFERRED}: queues one item delivery per player in the same transaction, handed over when the
 *       player is online; queued once, each delivery is claimed and handed over once.
 *   <li>{@link SweepMode#PROVIDER}: records the chunk's players as handed out and returns them in {@link Step#calls}
 *       for the runner to pay through the other plugin, then {@link #settle} records what those calls did. Calls to
 *       another plugin are not part of any transaction: a runner that stops between handing a chunk out and
 *       settling it leaves those players unknown, and whoever next advances the sweep counts them as uncertain and
 *       logs them instead of paying them again (at most once, never twice).
 * </ul>
 *
 * <p>A runner locks the journal row before each chunk, so two runners of one sweep take turns. A currency has at most
 * one unfinished sweep on every server sharing the database: a second is refused while one is running or paused, so a
 * repeated command cannot pay everyone twice and a giveall and a resetall of one currency never interleave.
 *
 * <p>Every method is database I/O and runs off the tick and region threads.
 */
public interface AccountSweep {

    /**
     * Record a new sweep in the journal, with nothing changed yet, unless {@code amount}'s currency already has an
     * unfinished one. A {@link SweepKind#CREDIT} adds {@code amount} and leaves out an account it would push past the
     * currency's {@code max}; a {@link SweepKind#RESET} sets every balance to {@code amount}, and an account already
     * there is not changed. {@code scope} matters to the ledger only: items and another plugin's accounts are reached
     * through the players.
     */
    Opening open(
            SweepKind kind,
            SweepScope scope,
            SweepMode mode,
            Money amount,
            EconomyReason reason,
            PlayerRef actor,
            long at);

    /**
     * Do the next chunk of {@code sweep} in one transaction, or mark the sweep done when none is left. When the
     * database refuses the chunk's rows for good (a figure a column cannot hold, a constraint), the sweep is marked
     * {@link SweepState#FAILED} and the step says {@link Progress#FAILED}. A failure that may pass (a lost connection)
     * is thrown, and the chunk changed nothing.
     */
    Step advance(Sweep sweep);

    /**
     * Record what the calls of the chunk {@link #advance} last handed out for {@code sweep} did, and release that
     * chunk. Only for {@link SweepMode#PROVIDER}.
     */
    void settle(Sweep sweep, SweepOutcome done);

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
            SweepMode mode,
            Money amount,
            EconomyReason reason,
            PlayerRef actor,
            long startedAt) {

        public Sweep {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(mode, "mode");
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
            SweepMode mode,
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
            Objects.requireNonNull(mode, "mode");
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
     * @param changedOwners the accounts this chunk changed (for items: queued a delivery for), whose cached balances
     *     are now stale
     * @param calls the players this chunk handed out to be changed through another plugin, then {@link #settle}d; empty
     *     unless the sweep is a {@link SweepMode#PROVIDER} one
     * @param progress whether more is left, and who ended the sweep
     * @param error why the sweep failed, for {@link Progress#FAILED}; else null
     */
    record Step(
            SweepOutcome total,
            List<UUID> changedOwners,
            List<PlayerRef> calls,
            Progress progress,
            @Nullable String error) {

        public Step {
            Objects.requireNonNull(total, "total");
            changedOwners = List.copyOf(changedOwners);
            calls = List.copyOf(calls);
            Objects.requireNonNull(progress, "progress");
        }

        public Step(SweepOutcome total, List<UUID> changedOwners, Progress progress) {
            this(total, changedOwners, List.of(), progress, null);
        }
    }

    /** Where a sweep stands after a step. */
    enum Progress {
        /** A chunk was done (or handed out) and more accounts are left. */
        MORE,
        /** This step found no account left and marked the sweep done: its caller announces the result. */
        FINISHED,
        /** The sweep had already ended (done by another runner, cancelled, or failed); nothing was changed. */
        ENDED,
        /** This step's chunk was refused for good and the sweep marked failed; nothing of the chunk was changed. */
        FAILED,
        /** Another runner has a chunk of this sweep handed out and not yet settled; nothing was done, try later. */
        BUSY
    }

    /**
     * What a sweep did.
     *
     * @param changed the accounts whose balance changed (for items: the players a delivery was queued for)
     * @param skipped the accounts left as they were because the change would break the currency's limits, or the
     *     other plugin refused it
     * @param gone the accounts removed (a wallet purge) between being listed and being changed
     * @param uncertain the players a call to another plugin may or may not have changed: the runner stopped, or the
     *     call failed, before it could tell; each is logged
     */
    record SweepOutcome(int changed, int skipped, int gone, int uncertain) {

        public SweepOutcome {
            if (changed < 0 || skipped < 0 || gone < 0 || uncertain < 0) {
                throw new IllegalArgumentException(
                        "counts must not be negative: " + changed + ", " + skipped + ", " + gone + ", " + uncertain);
            }
        }

        public SweepOutcome(int changed, int skipped, int gone) {
            this(changed, skipped, gone, 0);
        }

        /** Nothing done yet. */
        public static SweepOutcome none() {
            return new SweepOutcome(0, 0, 0, 0);
        }

        /** This outcome and {@code other} added together. */
        public SweepOutcome plus(SweepOutcome other) {
            Objects.requireNonNull(other, "other");
            return new SweepOutcome(
                    changed + other.changed, skipped + other.skipped, gone + other.gone, uncertain + other.uncertain);
        }
    }
}
