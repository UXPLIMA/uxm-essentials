package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player was jailed or released on the origin backend, so the backend that has them connected applies it at once
 * instead of at their next join. The sentence itself (the jail, the duration) is read from the shared database on the
 * receiver, and the receiver acts only when the database agrees with {@link #jailed()}, so a frame overtaken by a
 * later change does nothing.
 *
 * @param originServer the backend that wrote the change
 * @param frameId identifies this change, so a copy delivered twice is applied once
 * @param target the jailed or released player
 * @param jailed true for a jail, false for a release
 */
public record JailChanged(String originServer, UUID frameId, UUID target, boolean jailed) implements NetworkMessage {

    public JailChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public MessageType type() {
        return MessageType.JAIL_CHANGED;
    }
}
