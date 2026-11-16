package com.uxplima.uxmessentials.messaging.application.port;

/**
 * What a {@link MutePolicy} knows about a player's mute. {@link #UNKNOWN} is an answer of its own rather than an
 * exception, so every gate decides what a mute it could not read means; each one holds the action back and tells the
 * player to try again, since letting a muted player through is the worse mistake.
 */
public enum MuteAnswer {

    /** The player is not muted right now. */
    NOT_MUTED,

    /** The player is muted right now. */
    MUTED,

    /** The mute could not be read (the database did not answer), so whether the player is muted is not known. */
    UNKNOWN
}
