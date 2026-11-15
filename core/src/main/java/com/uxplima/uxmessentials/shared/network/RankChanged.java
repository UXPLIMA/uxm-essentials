package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's rank pointer changed on the origin backend (a {@code /rankup}, {@code /prestige} or
 * {@code /setrank}), so a peer holding that player's pointer in memory must re-read it from the shared database.
 * The frame carries the player identity only; the pointer row is the source of truth.
 *
 * @param originServer the backend that made the change
 * @param player the player whose cached rank pointer peers must reload
 */
public record RankChanged(String originServer, UUID player) implements NetworkMessage {

    public RankChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
    }

    @Override
    public MessageType type() {
        return MessageType.RANK_CHANGED;
    }
}
