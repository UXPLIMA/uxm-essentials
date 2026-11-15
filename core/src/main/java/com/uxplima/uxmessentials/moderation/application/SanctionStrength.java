package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.TempbanState;

/**
 * Which of two sanctions of the same kind is the stronger one: the one that lasts longer, a sanction with no end
 * beating any timed one. An end is an {@link Optional} instant, empty meaning the sanction never ends.
 */
final class SanctionStrength {

    private SanctionStrength() {}

    /**
     * True when a sanction ending at {@code proposed} lasts past one ending at {@code current}. A tie is not
     * stronger: replacing a sanction with an equal one would only rewrite who issued it.
     */
    static boolean outlasts(Optional<Instant> proposed, Optional<Instant> current) {
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(current, "current");
        if (current.isEmpty()) {
            return false;
        }
        return proposed.map(end -> end.isAfter(current.get())).orElse(true);
    }

    /**
     * When a stored ban ends. A permanent ban shares the tempban row with an expiry of {@link Ban#PERMANENT_SPAN}
     * past its issue, and comparing that sentinel as a date would let a later permanent ban look longer than an
     * earlier one, so it reads as empty here.
     */
    static Optional<Instant> endOf(TempbanState.Active ban) {
        Objects.requireNonNull(ban, "ban");
        boolean permanent = !ban.until().isBefore(ban.issuedAt().plus(Ban.PERMANENT_SPAN));
        return permanent ? Optional.empty() : Optional.of(ban.until());
    }
}
