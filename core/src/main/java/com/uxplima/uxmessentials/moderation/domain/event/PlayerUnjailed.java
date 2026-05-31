package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player was released by {@code /unjail} (or on serving an online-only sentence in full).
 *
 * @param target who was released
 * @param at when the release happened
 */
public record PlayerUnjailed(PlayerRef target, Instant at) implements ModerationEvent {

    public PlayerUnjailed {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
    }
}
