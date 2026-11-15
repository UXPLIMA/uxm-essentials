package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the vanish context: whether {@code /seen} must keep a connected player's presence from a
 * viewer. A vanished player the viewer may not see reads as last seen, never as online, wherever on the network they
 * are. A port because it crosses into another context and its storage, and is the seam the tests drive.
 *
 * <p>With the vanish module disabled the wiring binds {@link #ALWAYS_VISIBLE}.
 */
public interface SeenVisibility {

    /** Nobody is hidden: the binding when vanish is disabled. */
    SeenVisibility ALWAYS_VISIBLE = (viewer, target) -> false;

    /**
     * Whether {@code target}, who is online somewhere on the network, is vanished and hidden from {@code viewer}. May
     * read the shared database, so it is asked off the tick.
     */
    boolean isHiddenFrom(PlayerRef viewer, PlayerRef target);
}
