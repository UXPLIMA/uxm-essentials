package com.uxplima.uxmessentials.vanish.application.port;

/**
 * Whether a peer backend is still running. A port because the answer comes from the bus heartbeat roster, which lives
 * with the transport outside the application.
 */
@FunctionalInterface
public interface PeerLiveness {

    /** Whether the backend named {@code serverId} has sent a heartbeat recently enough to count as running. */
    boolean isLive(String serverId);
}
