package com.uxplima.uxmessentials.economy.domain;

/**
 * Which accounts a sweep reaches. The ledger also holds accounts other plugins open through Vault for things that are
 * not players, such as a town's bank or a tax holding account, and a reset must not wipe those by default.
 */
public enum SweepScope {
    /** The accounts of players who have joined a server of this database. */
    PLAYERS,
    /** Every account, also those other plugins opened. */
    ALL
}
