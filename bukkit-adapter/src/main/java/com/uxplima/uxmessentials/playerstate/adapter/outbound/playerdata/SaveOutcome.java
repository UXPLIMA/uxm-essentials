package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

/** How an offline save, or the read before one, ended. Only {@link #SAVED} changed anything. */
public enum SaveOutcome {

    /** The edit is on disk, or there was nothing to change. */
    SAVED,

    /** The player is online on this server or is joining it, so their file belongs to the server. */
    PLAYER_PRESENT,

    /** The player has no data file on this server. */
    NO_DATA,

    /** A slot the edit changes, or the file's data version, is not what the window was opened with. */
    CHANGED,

    /** The file could not be read or written; the file is as it was. */
    FAILED,

    /** This server's internals could not be reached, so player data cannot be read here at all. */
    UNAVAILABLE
}
