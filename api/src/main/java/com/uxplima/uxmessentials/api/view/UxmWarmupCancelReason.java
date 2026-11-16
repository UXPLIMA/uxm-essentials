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

    /**
     * No more specific reason is known. The plugin names one of the other reasons for every warmup it cuts short, so
     * a listener does not see this from it; the constant stays so code written against it keeps compiling.
     */
    ABORTED,

    /** The player left the server. */
    QUIT,

    /** The player started another teleport, here or to another server, which replaced this one. */
    SUPERSEDED
}
