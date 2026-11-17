package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A line was kept on the origin backend for a player who was not there. The backend the player is on shows them their
 * kept lines now instead of at their next join. The frame carries the player identity only; the kept rows are the
 * source of truth, and taking them deletes each row first, so a backend that shows a line is the only one that does.
 *
 * @param originServer the backend that kept the line
 * @param recipient the player whose kept lines to show
 */
public record PlayerNoticesKept(String originServer, UUID recipient) implements NetworkMessage {

    public PlayerNoticesKept {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(recipient, "recipient");
    }

    @Override
    public MessageType type() {
        return MessageType.PLAYER_NOTICES_KEPT;
    }
}
