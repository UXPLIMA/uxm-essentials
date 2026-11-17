package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's stored skin changed on the origin backend (a {@code /skin} set, update, clear or drop, or a Bedrock
 * skin written at login), so a peer must forget its cached row for that player, and the backend the player is on
 * must dress them again. The frame carries the player identity only; the skin row is the source of truth.
 *
 * @param originServer the backend that made the change
 * @param player the player whose stored skin changed
 */
public record PlayerSkinChanged(String originServer, UUID player) implements NetworkMessage {

    public PlayerSkinChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
    }

    @Override
    public MessageType type() {
        return MessageType.PLAYER_SKIN_CHANGED;
    }
}
