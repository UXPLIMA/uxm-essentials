package com.uxplima.uxmessentials.presence.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's vanish state was flipped by {@code /vanish}. The {@code vanished} flag carries the new state so a
 * consumer need not query it back. The adapter reacts by hiding or revealing the player across every other
 * player's view (which the {@code canSee}-based messaging and teleport visibility seams then honour) and by
 * suppressing or restoring the fake join/quit line.
 *
 * @param subject the player whose vanish state changed
 * @param vanished the new vanish state — true means now hidden
 * @param at when the toggle happened
 */
public record VanishToggled(PlayerRef subject, boolean vanished, Instant at) implements PresenceEvent {

    public VanishToggled {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(at, "at");
    }
}
