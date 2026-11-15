package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the cross-server live effect of a jail. The jail record in the shared database already holds the
 * player on every backend at their next join; this tells the backend that has them connected now to apply a jail or
 * a release at once, and tells the staff of every backend about a jail hop that failed. A port because the bus is a
 * platform boundary {@code :core} never imports; a backend with the network off runs against {@link #NONE}.
 */
public interface JailSync {

    /** Announces nothing: with the network off the local player is the only one this backend can act on. */
    JailSync NONE = new JailSync() {
        @Override
        public void jailChanged(PlayerRef target, boolean jailed) {
            // No peers: an offline target is jailed or released at their next join from the database.
        }

        @Override
        public void hopFailed(PlayerRef target, String jail) {
            // No peers: the staff of this backend were already told.
        }
    };

    /** Announce that {@code target} was jailed ({@code jailed}) or released, after the record was written. */
    void jailChanged(PlayerRef target, boolean jailed);

    /** Announce that {@code target} could not be taken to {@code jail} on its backend, for every peer's staff. */
    void hopFailed(PlayerRef target, String jail);
}
