package com.uxplima.uxmessentials.vaults.domain;

/**
 * The closed set of reasons a vault operation is refused. Each maps to one {@code VaultsMessageKey} the
 * application layer renders for the player; the aggregate returns one of these in a {@code Result} rather than
 * throwing, so a refused open is an ordinary outcome, not an exception.
 */
public enum VaultError {

    /** The requested vault index exceeds the owner's resolved {@code uxmessentials.vault.amount.<n>} quota. */
    AMOUNT_EXCEEDED,

    /** The owner owns no vaults yet, so {@code /vault} with no index has nothing to list or default to. */
    NONE_OWNED
}
