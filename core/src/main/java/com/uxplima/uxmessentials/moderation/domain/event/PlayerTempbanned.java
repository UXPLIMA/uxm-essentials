package com.uxplima.uxmessentials.moderation.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player was tempbanned by {@code /tempban}. The {@code ban} carries the wall-clock expiry.
 *
 * @param target who was banned
 * @param ban the applied tempban
 * @param at when the ban was applied
 */
public record PlayerTempbanned(PlayerRef target, TempbanState.Active ban, Instant at) implements ModerationEvent {

    public PlayerTempbanned {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(ban, "ban");
        Objects.requireNonNull(at, "at");
    }
}
