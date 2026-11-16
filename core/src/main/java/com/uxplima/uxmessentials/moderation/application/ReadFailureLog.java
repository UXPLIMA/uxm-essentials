package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * Logs the failed mute reads of a database outage without flooding the log: each kind of failure is written at most
 * once per {@link #QUIET}, and the next line says how many were left out meanwhile. A read that succeeds ends the
 * outage, so the next failure is written at once with a fresh count.
 *
 * <p>The quiet spell is timed on a monotonic clock, so a wall clock set back during an outage cannot keep it quiet.
 */
final class ReadFailureLog {

    /** How long a logged failure keeps the same kind of failure out of the log. */
    static final Duration QUIET = Duration.ofMinutes(1);

    private final Logger log;
    private final LongSupplier nanoTime;
    private final Map<String, Spell> spells = new HashMap<>();
    private volatile boolean failing;

    ReadFailureLog(Logger log, LongSupplier nanoTime) {
        this.log = Objects.requireNonNull(log, "log");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** A read failed: log {@code event} with {@code detail} unless the same event was logged within the quiet spell. */
    synchronized void failed(String event, String detail, RuntimeException failure) {
        failing = true;
        long now = nanoTime.getAsLong();
        Spell spell = spells.get(event);
        if (spell != null && now - spell.loggedAt() < QUIET.toNanos()) {
            spells.put(event, new Spell(spell.loggedAt(), spell.unlogged() + 1));
            return;
        }
        int unlogged = spell == null ? 0 : spell.unlogged();
        log.error("event=" + event + " " + detail + " unlogged=" + unlogged, failure);
        spells.put(event, new Spell(now, 0));
    }

    /** A read succeeded: the outage is over, and what was left out of the log is said once. */
    void succeeded() {
        if (!failing) {
            return;
        }
        synchronized (this) {
            int unlogged = spells.values().stream().mapToInt(Spell::unlogged).sum();
            if (unlogged > 0) {
                log.info("event=mute_read_recovered unlogged=" + unlogged);
            }
            spells.clear();
            failing = false;
        }
    }

    /** When an event was last logged, and how many of it were left out since. */
    private record Spell(long loggedAt, int unlogged) {}
}
