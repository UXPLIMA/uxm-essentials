package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.UUID;

/**
 * One entry on a player's ignore list.
 *
 * <p>An ignore is one-way: it says the owner of the list will not hear from this player, and says nothing about
 * the other direction.
 *
 * @param playerId the player being ignored
 * @param scope how much of their traffic is suppressed
 */
public record UxmIgnore(UUID playerId, UxmIgnoreScope scope) {

    public UxmIgnore {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(scope, "scope");
    }
}
