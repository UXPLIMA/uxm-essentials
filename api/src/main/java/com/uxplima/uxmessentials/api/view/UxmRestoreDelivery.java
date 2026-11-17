package com.uxplima.uxmessentials.api.view;

/** When a restored inventory reaches its player. */
public enum UxmRestoreDelivery {

    /** The player was online on this server, and now holds the restored inventory. */
    APPLIED,

    /**
     * The player was not online on this server. The restore is kept and put on them as they next join a server that
     * shares the database, or at once by the server they are on now.
     */
    KEPT
}
