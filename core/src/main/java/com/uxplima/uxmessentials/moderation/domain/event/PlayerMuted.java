package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player was muted by {@code /mute} or {@code /tempmute}. The {@code mute} carries whether it is permanent
 * or timed; {@code at} is when it was applied.
 *
 * @param target who was muted
 * @param mute the applied mute state (permanent or timed)
 * @param at when the mute was applied
 */
public record PlayerMuted(PlayerRef target, MuteState mute, Instant at) implements ModerationEvent {

    public PlayerMuted {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(at, "at");
    }
}
