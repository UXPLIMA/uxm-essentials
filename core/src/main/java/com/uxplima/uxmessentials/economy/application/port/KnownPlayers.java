package com.uxplima.uxmessentials.economy.application.port;

/**
 * The players a sweep over every player reaches: every player who joined a server of the database. Each server
 * records its joins; this adds the players the server itself knows from before it recorded them.
 */
public interface KnownPlayers {

    /** Record every player this server knows that the shared list does not have yet. Off the tick; best effort. */
    void recordAll();

    /** Records nothing, for a server or a test with no list of its own. */
    static KnownPlayers none() {
        return () -> {};
    }
}
