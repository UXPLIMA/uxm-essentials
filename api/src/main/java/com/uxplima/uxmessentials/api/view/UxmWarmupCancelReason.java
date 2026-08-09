package com.uxplima.uxmessentials.api.view;

/** Why a teleport warmup was cut short before it could complete. */
public enum UxmWarmupCancelReason {

    /** The player moved off the block they started on. */
    MOVED,

    /** The player turned on the spot, on a server that counts that as moving. */
    ROTATED,

    /** The player took damage. */
    DAMAGED,

    /** The player interacted with something. */
    INTERACTED,

    /** The warmup was called off outright, by a command or by the plugin shutting down. */
    ABORTED
}
