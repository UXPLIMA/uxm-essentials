package com.uxplima.uxmessentials.economy.application.port;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * Changes one currency's balance on every account the ledger knows: online or offline, on this backend or on any
 * other sharing the database. The bulk eco-admin verbs go through here, because a roster of the players online on
 * one server is not "every account".
 *
 * <p>A sweep is one unit of work: every balance it changes and the one ledger row it writes for each changed account
 * commit together or not at all. A server that stops part way through therefore leaves no account half done, and
 * running the verb again cannot pay anyone twice. Every write is made against the stored row under the database's
 * row lock, so a payment racing the sweep is never lost.
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
     * Credit {@code amount} to every known account, opening the balance of an account that never held the currency.
     * An account the credit would push past the currency's {@code max} is left as it was and counted as skipped.
     */
    SweepOutcome creditEvery(Money amount, EconomyReason reason, long at);

    /**
     * Set every known account's balance to {@code balance}. An account already holding exactly {@code balance} is not
     * changed and gets no ledger row; one with no balance yet counts as holding zero.
     */
    SweepOutcome resetEvery(Money balance, EconomyReason reason, long at);

    /** A sweep that reaches no currency, for a server whose balances are kept by another economy plugin. */
    static AccountSweep unreachable() {
        return new AccountSweep() {
            @Override
            public boolean reaches(Currency currency) {
                return false;
            }

            @Override
            public SweepOutcome creditEvery(Money amount, EconomyReason reason, long at) {
                throw new IllegalStateException("no currency is kept in this plugin's ledger");
            }

            @Override
            public SweepOutcome resetEvery(Money balance, EconomyReason reason, long at) {
                throw new IllegalStateException("no currency is kept in this plugin's ledger");
            }
        };
    }

    /**
     * What a sweep did.
     *
     * @param changed the accounts whose balance changed, each with one ledger row
     * @param skipped the accounts left as they were because the change would break the currency's limits
     */
    record SweepOutcome(int changed, int skipped) {

        public SweepOutcome {
            if (changed < 0 || skipped < 0) {
                throw new IllegalArgumentException("counts must not be negative: " + changed + ", " + skipped);
            }
        }

        /** Nothing changed and nothing skipped. */
        public static SweepOutcome none() {
            return new SweepOutcome(0, 0);
        }

        /** This outcome and {@code other} added together. */
        public SweepOutcome plus(SweepOutcome other) {
            Objects.requireNonNull(other, "other");
            return new SweepOutcome(changed + other.changed, skipped + other.skipped);
        }
    }
}
