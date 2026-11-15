package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A managed world changed on the origin backend (created, imported, adopted, claimed, a setting changed, removed), so
 * peers reload their cached copy of the network's worlds from the shared database. The whole set is what a peer
 * reloads; the world and its server are carried for logging context.
 *
 * @param originServer the backend that made the change
 * @param changeId identifies the change, so a copy delivered twice reloads once
 * @param server the backend whose world changed
 * @param world the world that changed, or {@code *} for a change to several at once
 */
public record WorldChanged(String originServer, UUID changeId, String server, String world) implements NetworkMessage {

    /** The world named when one change touched several worlds at once. */
    public static final String SEVERAL = "*";

    public WorldChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(world, "world");
    }

    @Override
    public MessageType type() {
        return MessageType.WORLD_CHANGED;
    }
}
