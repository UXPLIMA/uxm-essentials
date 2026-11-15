package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port to the vanish authority, for a request or staff teleport that crosses servers: the asking backend
 * sends the asker's see level along, and the backend a player is on judges them with its own vanish state, so a
 * vanished player looks offline to someone who may not see them. Before anyone is sent to another backend, the
 * shared vanish row of the player there is read too, so a hidden player's server is never revealed by the move. A
 * port because vanish is another context that wires after this one and may be off; tests bind a fake.
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

        @Override
        public boolean hiddenElsewhere(PlayerRef target, int viewerSeeLevel) {
            return false;
        }
    };

    /** The see level of someone who sees every vanished player: the console. */
    int SEES_EVERYONE = Integer.MAX_VALUE;

    /**
     * The vanish see level {@code viewer} is measured by on this backend; 0 when they hold no see node, and
     * {@link #SEES_EVERYONE} for the console.
     */
    int seeLevel(PlayerRef viewer);

    /** Whether {@code target}, a player on this backend, is hidden from {@code viewer} at {@code viewerSeeLevel}. */
    boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel);

    /**
     * Whether {@code target}, a player on another backend, is hidden at {@code viewerSeeLevel}, by the vanish row every
     * backend keeps in the shared database. Blocks on I/O, so it is asked off the tick.
     */
    boolean hiddenElsewhere(PlayerRef target, int viewerSeeLevel);
}
