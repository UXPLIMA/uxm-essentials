package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player was jailed by {@code /jail}. The {@code jail} carries the named location and whether the sentence
 * is permanent, online-only timed, or wall-clock timed.
 *
 * @param target who was jailed
 * @param jail the applied jail sentence
 * @param at when the jail was applied
 */
public record PlayerJailed(PlayerRef target, JailState.Active jail, Instant at) implements ModerationEvent {

    public PlayerJailed {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(at, "at");
    }
}
