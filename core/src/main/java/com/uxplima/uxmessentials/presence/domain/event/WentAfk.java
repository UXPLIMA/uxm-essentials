package com.uxplima.uxmessentials.presence.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player entered the AFK state, either by running {@code /afk} or by the idle sweep flipping them after the
 * configured threshold. The {@code automatic} flag tells the two apart: a manual {@code /afk} may carry a
 * {@code reason}, the auto sweep never does. Other plugins and the status-placeholder path observe this to
 * reflect the new state.
 *
 * @param subject the player who went AFK
 * @param reason the reason from a manual {@code /afk}, or empty for an automatic transition
 * @param automatic true when the idle sweep flipped the player, false for an explicit {@code /afk}
 * @param at when the transition happened
 */
public record WentAfk(PlayerRef subject, Optional<String> reason, boolean automatic, Instant at)
        implements PresenceEvent {

    public WentAfk {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
    }
}
