package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's home set changed on the origin backend (a {@code /sethome}, {@code /delhome}, rename, or
 * move), so peers must drop their cached copy of {@code owner}'s homes and re-read the authoritative rows on
 * the next {@code /home} / {@code /homes}. The frame carries the owner identity only; the home rows live in
 * the shared database, and a network-scoped {@code /home} on a peer resolves the location from there and
 * brokers the cross-server teleport through the proxy.
 *
 * @param originServer the backend that made the change
 * @param owner the home owner whose cached home set peers must invalidate
 */
public record HomeChanged(String originServer, UUID owner) implements NetworkMessage {

    public HomeChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(owner, "owner");
    }

    @Override
    public MessageType type() {
        return MessageType.HOME_CHANGED;
    }
}
