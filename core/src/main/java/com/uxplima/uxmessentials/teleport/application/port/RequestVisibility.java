package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port to the vanish authority, for a request that crosses servers: the requester's backend sends the
 * requester's see level along, and the target's backend judges its own vanished player with it, so a vanished target
 * looks offline to a requester who may not see them. A port because vanish is another context that wires after this
 * one and may be off; tests bind a fake.
 */
public interface RequestVisibility {

    /** Nobody is hidden: what a server without the vanish module means. */
    RequestVisibility EVERYONE = new RequestVisibility() {
        @Override
        public int seeLevel(PlayerRef viewer) {
            return 0;
        }

        @Override
        public boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel) {
            return false;
        }
    };

    /** The vanish see level {@code viewer} is measured by on this backend; 0 when they hold no see node. */
    int seeLevel(PlayerRef viewer);

    /** Whether {@code target}, a player on this backend, is hidden from {@code viewer} at {@code viewerSeeLevel}. */
    boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel);
}
