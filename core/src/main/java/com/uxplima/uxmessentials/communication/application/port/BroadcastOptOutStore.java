package com.uxplima.uxmessentials.communication.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the set of players who have opted out of announcer broadcasts via {@code /broadcasttoggle}.
 * The default is opted-in: a player not present in the store receives announcements. The choice is kept in the
 * database every backend shares and held in memory for the players online here, so a read on the announcer's fan-out
 * thread never waits on storage.
 *
 * <p>The store holds only the subscription bit (no template content, no schedule), the player's own choice, not
 * operator data. {@link #toggle} flips the bit and returns the new state in one atomic step,
 * which the {@code BroadcastOptOut} use case reports back to the player.
 */
public interface BroadcastOptOutStore {

    /** Whether {@code who} currently receives announcer broadcasts (the default for an unknown player). */
    boolean receivesBroadcasts(PlayerRef who);

    /**
     * Flip {@code who}'s subscription atomically and return the new opt-out state: {@code true} when the player is
     * now opted out (will no longer receive broadcasts), {@code false} when they are opted back in.
     */
    boolean toggle(PlayerRef who);

    /**
     * Whether {@code who}'s setting was read on this server, so it can be changed. A change asked for before then is
     * refused rather than built on a setting this server does not know.
     */
    default boolean ready(PlayerRef who) {
        return true;
    }

    /** Drop {@code who}'s entry; called on quit so a disconnected player holds no transient subscription state. */
    void forget(PlayerRef who);
}
