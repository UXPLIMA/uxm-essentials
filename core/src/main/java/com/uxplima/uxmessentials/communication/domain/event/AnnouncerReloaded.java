package com.uxplima.uxmessentials.communication.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * The operator reloaded the announcer schedule (a {@code /uxmess reload communication} or the module's own reload
 * path swapped a fresh {@code AnnouncerSchedule} in). The {@code lineCount} records how many broadcast lines the
 * new schedule carries, for an audit line and for the reload confirmation — it is a count, not the operator's
 * template text. The running announcer timer reads the swapped schedule on its next tick.
 *
 * @param lineCount the number of broadcast lines in the reloaded schedule
 * @param at when the reload happened
 */
public record AnnouncerReloaded(int lineCount, Instant at) implements CommunicationEvent {

    public AnnouncerReloaded {
        Objects.requireNonNull(at, "at");
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must not be negative: " + lineCount);
        }
    }
}
