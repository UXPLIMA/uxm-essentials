package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player was frozen or released on the origin backend. A freeze is held in memory, so every backend that hears this
 * keeps the player frozen too and they cannot walk free by switching servers; a release lifts it on every backend. The
 * proxy plugin reads the same frame to keep a frozen player on the server they were frozen on.
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
 * @param heldOn the backend the player was connected to when the change was made, where the proxy keeps a frozen
 *     player; the origin when they were connected nowhere, and for a frame from a backend that predates it
 */
public record FreezeChanged(
        String originServer,
        UUID frameId,
        UUID target,
        String targetName,
        boolean frozen,
        long changedAtMillis,
        String heldOn)
        implements NetworkMessage {

    public FreezeChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(heldOn, "heldOn");
    }

    /** A change for a player held on the origin backend. */
    public FreezeChanged(
            String originServer, UUID frameId, UUID target, String targetName, boolean frozen, long changedAtMillis) {
        this(originServer, frameId, target, targetName, frozen, changedAtMillis, originServer);
    }

    @Override
    public MessageType type() {
        return MessageType.FREEZE_CHANGED;
    }
}
