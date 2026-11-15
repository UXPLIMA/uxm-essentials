package com.uxplima.uxmessentials.teleport.application.port;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.teleport.application.RemoteTeleport;

/**
 * Outbound port to the cross-server bus for teleport requests and staff teleports that span two backends (a platform
 * boundary, and the test seam that carries frames between fake backends). The adapter turns each
 * {@link RemoteTeleport} into its wire frame and back, so the teleport application never names a frame type.
 *
 * <p>A frame reaches every peer, possibly more than once and out of order, and never comes back to the backend
 * that sent it; the receiver decides by the addressed server whether a message is for it.
 */
public interface TeleportNetwork {

    /** The {@code server-name} of this backend, which messages are addressed by. */
    String localServer();

    /** Publish {@code message} to every peer. */
    void send(RemoteTeleport message);

    /** Hand every message a peer sent to {@code handler}, on the bus's dispatch thread, never a region thread. */
    void subscribe(Consumer<RemoteTeleport> handler);
}
