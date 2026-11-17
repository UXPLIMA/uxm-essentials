package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One player's shared settings changed on the origin backend: a switch such as {@code /msgtoggle} or
 * {@code /socialspy}, or the players they watch with {@code /socialspy <player>}. A peer holding that player's
 * settings, because the player is on it or on their way to it, reads them again from the shared database. The frame
 * carries only who changed, since the rows already hold what.
 *
 * @param originServer the backend that made the change
 * @param player the player whose settings changed
 */
public record PlayerSettingsChanged(String originServer, UUID player) implements NetworkMessage {

    public PlayerSettingsChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
    }

    @Override
    public MessageType type() {
        return MessageType.PLAYER_SETTINGS_CHANGED;
    }
}
