package com.uxplima.uxmessentials.api.view;

/** What kind of teleport a movement was, which is how a listener tells {@code /back} from {@code /home}. */
public enum UxmTeleportKind {

    /** One player accepting another's {@code /tpa}. */
    REQUEST,

    /** {@code /back}, returning to a captured position. */
    BACK,

    /** {@code /rtp}, to a searched-for safe position. */
    RANDOM,

    /** {@code /spawn}. */
    SPAWN,

    /** {@code /home}, to one of the player's own or a home they were invited to. */
    HOME,

    /** {@code /warp} or {@code /pwarp}. */
    WARP,

    /** A respawn after death. */
    RESPAWN,

    /** A staff member moving somebody, including {@code /tp} and {@code /tphere}. */
    ADMIN,

    /** A teleport to typed coordinates rather than to a named destination. */
    POSITIONAL
}
