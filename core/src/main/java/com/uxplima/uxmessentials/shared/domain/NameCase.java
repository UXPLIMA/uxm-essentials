package com.uxplima.uxmessentials.shared.domain;

/**
 * Whether two player names that differ only in case belong to one account or to two on this server.
 *
 * <p>An authenticated login (online mode, here or at the proxy) carries the account Mojang owns, and Mojang keeps
 * names unique in any case, so {@code Steve} and {@code steve} are the same player at any moment. An offline-mode
 * login derives its account from the exact spelling, so each case variant is a player of its own.
 */
public enum NameCase {

    /** Authenticated logins: a name names one account whatever its case. */
    INSENSITIVE,

    /** Offline-mode logins: every spelling of a name is its own account. */
    SENSITIVE
}
