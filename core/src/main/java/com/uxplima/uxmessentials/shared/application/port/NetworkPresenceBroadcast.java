package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.networkpresence.RosterStamp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells the other backends who joined and left this one. A port because the cross-server bus lives in an adapter;
 * the adapter turns each call into a presence frame. Fire and forget: a lost call is repaired by the next
 * {@link #roster} the heartbeat sends.
 */
public interface NetworkPresenceBroadcast {

    /** A broadcast with no peers, for a backend that runs without the network. */
    NetworkPresenceBroadcast NONE = new NetworkPresenceBroadcast() {
        @Override
        public void joined(RosterStamp stamp, PlayerRef player) {
            // No peers to tell.
        }

        @Override
        public void left(RosterStamp stamp, PlayerRef player) {
            // No peers to tell.
        }

        @Override
        public void roster(RosterStamp stamp, boolean replyRequested, List<PlayerRef> players) {
            // No peers to tell.
        }
    };

    /** {@code player} joined this backend. */
    void joined(RosterStamp stamp, PlayerRef player);

    /** {@code player} left this backend. */
    void left(RosterStamp stamp, PlayerRef player);

    /** Every player on this backend; with {@code replyRequested} each peer answers with its own roster. */
    void roster(RosterStamp stamp, boolean replyRequested, List<PlayerRef> players);
}
