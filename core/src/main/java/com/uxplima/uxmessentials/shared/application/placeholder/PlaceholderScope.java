package com.uxplima.uxmessentials.shared.application.placeholder;

/** Who a key answers about, which is what decides whether it holds a value away from the server. */
public enum PlaceholderScope {

    /** Reads durable per-player data, so it answers for an offline player too. */
    PLAYER,

    /** Reads live session state, so it reads the dash while the player is offline. */
    SESSION,

    /** Reads a server-wide value; the requesting player is ignored. */
    GLOBAL,

    /**
     * Reads the relation between two players rather than one player's own state, so it is typed with the
     * {@code rel_} prefix and only answers where PlaceholderAPI supplies both sides (a chat format, a tab or
     * nametag line rendered per viewer).
     */
    RELATIONAL
}
