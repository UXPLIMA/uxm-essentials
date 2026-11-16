package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

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
     * Record a new sweep in the journal, with nothing changed yet. A {@link SweepKind#CREDIT} adds {@code amount}
     * (opening the balance of an account that never held the currency) and leaves out an account it would push past
     * the currency's {@code max}; a {@link SweepKind#RESET} sets every balance to {@code amount}, and an account
     * already there is not changed.
     */
    Sweep open(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at);

    /** Change the next chunk of {@code sweep}'s accounts in one transaction, or mark the sweep done when none is left. */
    Step advance(Sweep sweep);

    /** The sweeps the journal still has running, in the order they were opened. */
    List<Sweep> unfinished();

    /** A sweep that reaches no currency, for a server whose balances are kept by another economy plugin. */
    static AccountSweep unreachable() {
        return new AccountSweep() {
            @Override
            public boolean reaches(Currency currency) {
                return false;
            }

            @Override
            public Sweep open(
                    SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at) {
                throw new IllegalStateException("no currency is kept in this plugin's ledger");
            }

            @Override
            public Step advance(Sweep sweep) {
                throw new IllegalStateException("no currency is kept in this plugin's ledger");
            }

            @Override
            public List<Sweep> unfinished() {
                return List.of();
            }
        };
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
     * What one call to {@link #advance} did.
     *
     * @param total the sweep's totals so far, across every runner
     * @param changedOwners the accounts this chunk changed, whose cached balances are now stale
     * @param progress whether more is left, and who finished the sweep
     */
    record Step(SweepOutcome total, List<UUID> changedOwners, Progress progress) {

        public Step {
            Objects.requireNonNull(total, "total");
            changedOwners = List.copyOf(changedOwners);
            Objects.requireNonNull(progress, "progress");
        }
    }

    /** Where a sweep stands after a step. */
    enum Progress {
        /** A chunk was done and more accounts are left. */
        MORE,
        /** This step found no account left and marked the sweep done: its caller announces the result. */
        FINISHED,
        /** Another runner had already marked the sweep done; nothing was changed. */
        ALREADY_FINISHED
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
