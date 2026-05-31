package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's mute was lifted by {@code /unmute}.
 *
 * @param target who was unmuted
 * @param at when the mute was lifted
 */
public record PlayerUnmuted(PlayerRef target, Instant at) implements ModerationEvent {

    public PlayerUnmuted {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
    }
}
