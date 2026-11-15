package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;

/**
 * An online player and the backend they are connected to.
 *
 * @param player the player
 * @param server the {@code server-name} of the backend the player is on
 */
public record OnlinePlayer(PlayerRef player, String server) {

    public OnlinePlayer {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        if (server.isBlank()) {
            throw new IllegalArgumentException("server must not be blank");
        }
    }
}
