package com.uxplima.uxmessentials.moderation.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player was warned by {@code /warn}. The {@code warn} carries the issuer, reason and instant; the count
 * is the target's total number of warnings after this one was appended.
 *
 * @param target who was warned
 * @param warn the appended warning
 * @param totalWarnings the target's warning count after this one
 */
public record PlayerWarned(PlayerRef target, Warn warn, int totalWarnings) implements ModerationEvent {

    public PlayerWarned {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(warn, "warn");
        if (totalWarnings < 1) {
            throw new IllegalArgumentException("totalWarnings must be at least 1 after a warn");
        }
    }
}
