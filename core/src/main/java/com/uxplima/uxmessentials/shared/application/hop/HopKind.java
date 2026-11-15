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

    /** Another player's position. */
    PLAYER
}
