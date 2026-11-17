package com.uxplima.uxmessentials.economy.domain;

/**
 * How a sweep over every account reaches the balances, which follows from where the currency's money is kept.
 */
public enum SweepMode {
    /** The balances are rows of this plugin's ledger: each chunk changes them and writes their ledger rows at once. */
    LEDGER,
    /**
     * The balance can only be changed while its player is online: items in an inventory, or a backend such as
     * experience that has no offline account. Each chunk queues one delivery per player, handed over when they are
     * online on a server of the database.
     */
    DEFERRED,
    /**
     * Another plugin keeps the balances, offline ones included (a Vault or Treasury economy, or a currency backend of
     * another plugin): each chunk hands a group of players to the runner, which pays them one by one through it.
     */
    PROVIDER
}
