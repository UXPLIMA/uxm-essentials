package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * The proxy plugin telling the backend a player just connected to how they got there: on which proxy connection, and
 * whether it is a new login or a switch from another backend on a connection that was already open. It is sent to
 * that backend alone, over the player's connection, and sealed with {@code network.bus.secret} like a backend's frame,
 * so a client cannot write one.
 *
 * <p>A connection id is made by the proxy when the player logs in and kept until they disconnect, so a backend can
 * tell a switch on the connection a second factor was proved over from a new login.
 *
 * @param originServer {@link #PROXY}, the sender
 * @param frameId identifies this frame, so a copy delivered twice acts once
 * @param player the player who connected
 * @param connection the proxy connection they are on
 * @param server the backend they connected to, the one this frame is for
 * @param switched true for a switch from another backend, false for a new login
 */
public record ProxyConnectionFrame(
        String originServer, UUID frameId, UUID player, UUID connection, String server, boolean switched)
        implements NetworkMessage {

    /** The origin the proxy plugin stamps on its frames; no backend uses it as its server name. */
    public static final String PROXY = "uxmessentials-velocity";

    public ProxyConnectionFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(server, "server");
    }

    @Override
    public MessageType type() {
        return MessageType.PROXY_CONNECTION;
    }
}
