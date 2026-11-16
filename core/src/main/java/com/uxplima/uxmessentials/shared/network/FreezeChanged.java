package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player was frozen or released on the origin backend. A freeze is held in memory, so every backend that hears this
 * keeps the player frozen too and they cannot walk free by switching servers; a release lifts it on every backend. The
 * proxy plugin reads the same frame to keep a frozen player on the server that froze them.
 *
 * <p>Two changes to one player can arrive out of order (the proxy and Redis both carry them), so the receiver keeps
 * the change with the latest {@code changedAtMillis} and ignores an older one.
 *
 * @param originServer the backend that made the change
 * @param frameId identifies this change, so a copy delivered twice is applied once
 * @param target the frozen or released player
 * @param targetName the player's name when the change was made
 * @param frozen true for a freeze, false for a release
 * @param changedAtMillis when the change was made, in epoch milliseconds
 */
public record FreezeChanged(
        String originServer, UUID frameId, UUID target, String targetName, boolean frozen, long changedAtMillis)
        implements NetworkMessage {

    public FreezeChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetName, "targetName");
    }

    @Override
    public MessageType type() {
        return MessageType.FREEZE_CHANGED;
    }
}
