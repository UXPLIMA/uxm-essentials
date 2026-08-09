package com.uxplima.uxmessentials.api.view;

/** Whether a player warp is usable at all, whatever its access setting says. */
public enum UxmPlayerWarpStatus {

    /** Usable, subject to its access setting. */
    ACTIVE,

    /** Temporarily closed, typically because its rent lapsed or a moderator closed it. */
    SUSPENDED,

    /** Taken out of circulation and kept only for the record. */
    ARCHIVED
}
