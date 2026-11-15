package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled gate to the vanish context: whether a {@code target} is hidden from a {@code viewer} by
 * vanish. A vanished player is hidden from {@code /msg} target resolution. A sender who cannot see them
 * resolves them as if they were offline. Staff holding the vanish-see node still see, and so can still message, a vanished target.
 *
 * <p>The coupling is soft: when the vanish module is disabled (or has not yet landed) the wiring binds
 * {@link #ALWAYS_VISIBLE}, so messaging degrades to "no one is hidden" rather than failing, mirroring the
 * teleport context, whose {@code /tpa} is likewise vanish-aware through a check over the same authority that
 * degrades when vanish is off.
 */
public interface VanishVisibility {

    /** A policy under which nothing is ever hidden: the binding when vanish is disabled. */
    VanishVisibility ALWAYS_VISIBLE = (viewer, target) -> false;

    /** True when {@code target} is vanished and {@code viewer} cannot see them (so cannot message them). */
    boolean isHiddenFrom(PlayerRef viewer, PlayerRef target);

    /**
     * The vanish see level {@code viewer} holds here, carried with a message to another backend so that backend can
     * apply its own vanish to them. {@code 0} sees no vanished player, which is also the answer with vanish off.
     */
    default int seeLevel(PlayerRef viewer) {
        return 0;
    }

    /**
     * Whether {@code target}, vanished on this backend, is hidden from {@code viewer} on another backend who holds
     * {@code viewerSeeLevel} there. Only this backend knows for sure who it hides, which is why a message from another
     * backend asks here rather than trusting the sender's view.
     */
    default boolean isHiddenHereFrom(PlayerRef viewer, int viewerSeeLevel, PlayerRef target) {
        return false;
    }
}
