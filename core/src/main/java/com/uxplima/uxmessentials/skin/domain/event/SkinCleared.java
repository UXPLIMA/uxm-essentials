package com.uxplima.uxmessentials.skin.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's stored choice was dropped, so they are back to whatever the join order gives them.
 *
 * @param who the player whose choice was dropped
 * @param at when it was dropped
 */
public record SkinCleared(PlayerRef who, Instant at) implements SkinEvent {

    public SkinCleared {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(at, "at");
    }
}
