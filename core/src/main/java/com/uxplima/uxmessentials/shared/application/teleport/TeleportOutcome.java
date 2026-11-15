package com.uxplima.uxmessentials.shared.application.teleport;

/**
 * How one local teleport ended. A teleport port reports exactly one of these per request, and only {@link #LANDED}
 * means the player stands at the destination; every other outcome is a teleport that did not happen, so whatever
 * was paid for it is owed back.
 */
public enum TeleportOutcome {

    /** The player arrived at the destination. */
    LANDED,

    /** The warmup was stopped by a move, damage or an interaction, or the player left during it. */
    CANCELLED,

    /** The warmup was stopped because the same player started another teleport. */
    SUPERSEDED,

    /** The teleport rules or something outside the plugin refused the teleport before any warmup began. */
    REFUSED,

    /** The hop was issued after the warmup and the player did not arrive. */
    MISSED,

    /** The player was gone before the teleport could start on their thread. */
    RETIRED;

    /** True only when the player arrived. */
    public boolean landed() {
        return this == LANDED;
    }
}
