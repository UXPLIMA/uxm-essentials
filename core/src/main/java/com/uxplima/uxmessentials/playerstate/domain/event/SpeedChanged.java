package com.uxplima.uxmessentials.playerstate.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's walk or fly speed was set by {@code /speed}, {@code /walkspeed}, or {@code /flyspeed}. The
 * {@code kind} distinguishes which speed changed so a consumer (and the audit log) need not infer it.
 *
 * @param subject the player whose speed changed
 * @param actor the player who ran the command
 * @param kind whether the walk or the fly speed changed
 * @param value the new speed on the operator scale
 * @param at when the change happened
 */
public record SpeedChanged(PlayerRef subject, PlayerRef actor, Kind kind, SpeedValue value, Instant at)
        implements PlayerStateEvent {

    public SpeedChanged {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(at, "at");
    }

    /** Which of the two speeds the change applies to. */
    public enum Kind {
        WALK,
        FLY
    }
}
