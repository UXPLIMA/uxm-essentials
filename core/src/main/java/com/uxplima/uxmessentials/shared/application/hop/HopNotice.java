package com.uxplima.uxmessentials.shared.application.hop;

/** Every line a player can read about a cross-server hop. Each {@link HopResolver} maps them to its own keys. */
public enum HopNotice {

    /** The origin is handing the player to the proxy. */
    SENDING,

    /** The player stands at the destination. */
    ARRIVED,

    /** No backend took the player within the request time. */
    TIMED_OUT,

    /** The proxy put the player on a backend other than the destination's. */
    MISROUTED,

    /** The target could not move the player: the destination is gone, or the hop was refused or missed. */
    FAILED,

    /** The hop could not be started at all. */
    UNAVAILABLE,

    /**
     * The hop was not started because another hop of the same player is still under way. The destination is fine,
     * so this must not read like {@link #UNAVAILABLE}.
     */
    BUSY,

    /** The charge went back. */
    REFUNDED
}
