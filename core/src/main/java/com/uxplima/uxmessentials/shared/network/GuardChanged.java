package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A guard region changed on the origin backend. Peers that load the scope read the region again from the shared
 * database and recompile that world, so an edit on lobby-1 protects lobby-5 at once. The frame carries only the
 * key; the region itself lives in the database.
 *
 * @param originServer the backend that made the change
 * @param scope the region's scope ({@code server:<id>} or {@code set:<name>})
 * @param world the world name
 * @param region the region id
 */
public record GuardChanged(String originServer, String scope, String world, String region) implements NetworkMessage {

    public GuardChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(region, "region");
    }

    @Override
    public MessageType type() {
        return MessageType.GUARD_CHANGED;
    }
}
