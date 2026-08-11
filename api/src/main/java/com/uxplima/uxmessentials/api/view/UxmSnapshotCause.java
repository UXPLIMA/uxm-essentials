package com.uxplima.uxmessentials.api.view;

/** Why an inventory snapshot was taken. */
public enum UxmSnapshotCause {

    /** Taken because the player died, before the items dropped. */
    DEATH,

    /** Taken because the player left the server. */
    LOGOUT,

    /** Taken as the safety copy of what was there immediately before a restore overwrote it. */
    RESTORE
}
