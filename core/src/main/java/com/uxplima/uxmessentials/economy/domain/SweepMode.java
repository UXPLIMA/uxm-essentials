package com.uxplima.uxmessentials.economy.domain;

/**
 * How a sweep over every account reaches the balances, which follows from where the currency's money is kept.
 */
public enum SweepMode {
    /** The balances are rows of this plugin's ledger: each chunk changes them and writes their ledger rows at once. */
    LEDGER,
    /**
     * The balances are items in inventories: each chunk queues one delivery per player, handed over in their
     * inventory when they are online on a server of the database.
     */
    ITEMS,
    /**
     * Another plugin keeps the balances (a Vault or Treasury economy, or a currency backend of another plugin): each
     * chunk hands a group of players to the runner, which pays them one by one through that plugin.
     */
    PROVIDER
}
