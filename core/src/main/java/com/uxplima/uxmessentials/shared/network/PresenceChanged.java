package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player joined or left the origin backend. Presence has no shared table, so like {@link VanishStateChanged} this
 * frame carries the value itself: the player, the name they are connected under, and whether they are now online
 * there. A peer applies it to its in-memory view of who is on which backend.
 *
 * <p>Frames from one origin can arrive out of order (the plugin-messaging transport hands each one to its own async
 * task, and the {@code both} transport delivers every frame twice), so each carries the origin's {@code session} and
 * a {@code sequence} that grows with every presence frame the session sends. A peer applies a join or quit only when
 * its sequence is newer than both the last roster and the last frame it applied for that player, treats a newer
 * session as a restart and ignores frames from an older one.
 *
 * @param originServer the backend the player joined or left
 * @param session identifies one run of the origin backend; a new value means the origin restarted
 * @param sequence the position of this frame among the presence frames of {@code session}
 * @param player the player who joined or left
 * @param playerName the name the player is connected under
 * @param online {@code true} for a join, {@code false} for a quit
 */
public record PresenceChanged(
        String originServer, long session, long sequence, UUID player, String playerName, boolean online)
        implements NetworkMessage {

    public PresenceChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
    }

    @Override
    public MessageType type() {
        return MessageType.PRESENCE_CHANGED;
    }
}
