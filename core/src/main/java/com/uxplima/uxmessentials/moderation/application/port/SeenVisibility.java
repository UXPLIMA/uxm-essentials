package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the vanish context: whether {@code /seen} must keep a connected player's presence from a
 * viewer. A vanished player the viewer may not see reads as last seen, never as online, wherever on the network they
 * are. A port because it crosses into another context and its storage, and is the seam the tests drive.
 *
 * <p>The first two questions read permissions and the server's own vanish state, so they are asked on the command
 * thread; the third reads the shared database, so it is asked off the tick with the see level found earlier. With the
 * vanish module disabled the wiring binds {@link #ALWAYS_VISIBLE}.
 */
public interface SeenVisibility {

    /** A see level above every vanish level: what the console and other system actors see with. */
    int SEES_EVERYONE = Integer.MAX_VALUE;

    /** Nobody is hidden: the binding when vanish is disabled. */
    SeenVisibility ALWAYS_VISIBLE = new SeenVisibility() {
        @Override
        public int seeLevel(PlayerRef viewer) {
            return SEES_EVERYONE;
        }

        @Override
        public boolean isHiddenHere(PlayerRef viewer, int viewerSeeLevel, PlayerRef target) {
            return false;
        }

        @Override
        public boolean isHiddenElsewhere(int viewerSeeLevel, PlayerRef target) {
            return false;
        }
    };

    /** On the command thread: the vanish see level {@code viewer} is measured by; {@link #SEES_EVERYONE} for a system actor. */
    int seeLevel(PlayerRef viewer);

    /** On the command thread: whether {@code target}, online here, is vanished and hidden at {@code viewerSeeLevel}. */
    boolean isHiddenHere(PlayerRef viewer, int viewerSeeLevel, PlayerRef target);

    /**
     * Off the tick: whether {@code target}, online on another backend, is vanished there and hidden at
     * {@code viewerSeeLevel}. Reads the shared database.
     */
    boolean isHiddenElsewhere(int viewerSeeLevel, PlayerRef target);
}
