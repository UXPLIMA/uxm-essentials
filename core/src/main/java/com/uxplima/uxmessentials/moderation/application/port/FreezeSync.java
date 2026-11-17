package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for a freeze on a network. A freeze lives in memory on each backend, so a freeze or release made here
 * is announced for the other backends (and the proxy) to hold too: a frozen player who switches servers stays frozen,
 * and {@code /unfreeze} anywhere lifts it everywhere. A port because the bus is a platform boundary {@code :core}
 * never imports; a backend with the network off runs against {@link #NONE}.
 */
public interface FreezeSync {

    /** Announces nothing: with the network off the freeze held here is the only one. */
    FreezeSync NONE = new FreezeSync() {
        @Override
        public void freezeChanged(PlayerRef target, boolean frozen, Instant at, String heldOn) {
            // No peers to tell.
        }

        @Override
        public void cleared(Instant at) {
            // No proxy keeps a freeze for this backend.
        }
    };

    /**
     * Announce that {@code target} was frozen ({@code frozen}) or released here at {@code at}, while connected to the
     * backend {@code heldOn}, where the proxy keeps a frozen player.
     */
    void freezeChanged(PlayerRef target, boolean frozen, Instant at, String heldOn);

    /**
     * Announce that this backend holds no freeze as of {@code at}, because it just started; whatever the proxy kept
     * for a player frozen here before then is stale.
     */
    void cleared(Instant at);
}
