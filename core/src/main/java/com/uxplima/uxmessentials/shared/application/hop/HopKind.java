package com.uxplima.uxmessentials.shared.application.hop;

/**
 * What a cross-server hop is heading to. The kind picks the {@link HopResolver} that finds the destination on the
 * target backend, owns the money for the hop and words its notices, so one store and one arrival serve them all.
 */
public enum HopKind {

    /** A server warp. */
    WARP,

    /** A player warp. */
    PLAYER_WARP,

    /** One of a player's homes. */
    HOME,

    /** A world's entry point. */
    WORLD,

    /** A jail cell; the player did not choose this hop. */
    JAIL,

    /** Another player's position, reached through a teleport request that player or the mover accepted. */
    PLAYER_REQUEST,

    /** Another player's position, reached through a staff teleport; ruled as the staff teleport it stands for. */
    PLAYER_STAFF,

    /**
     * A kind a newer release wrote that this one does not know. No resolver ever serves it, so such a row is left for
     * its origin while it is fresh and claimed once it is abandoned, like any kind this backend cannot serve.
     */
    UNKNOWN
}
