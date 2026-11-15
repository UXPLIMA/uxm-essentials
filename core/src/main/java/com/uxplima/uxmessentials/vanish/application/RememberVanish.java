package com.uxplima.uxmessentials.vanish.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Writes each player's vanish to the shared {@link VanishRecords}, which the next backend they join reads: that is how
 * a vanish survives a server switch, and how a player who may persist it comes back hidden after a restart. A write is
 * stamped when the change happens and runs on the async lane, so a command never waits on the database and two writes
 * that finish out of order still settle on the later change.
 *
 * <p>It also remembers which online players changed their vanish on this backend, so a stored state that arrives late
 * (the login read timed out) never overrides a change the player made after joining.
 */
public final class RememberVanish {

    private final VanishRecords records;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Logger log;
    private final Set<UUID> changedHere = ConcurrentHashMap.newKeySet();

    public RememberVanish(VanishRecords records, Scheduler scheduler, Clock clock, Logger log) {
        this.records = Objects.requireNonNull(records, "records");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Keep {@code who} as vanished at {@code level}. */
    public void vanished(UUID who, VanishLevel level) {
        save(who, StoredVanish.vanished(level, clock.instant()));
    }

    /** Keep {@code who} as visible. */
    public void revealed(UUID who) {
        save(who, StoredVanish.visible(clock.instant()));
    }

    /** Record that the vanished {@code who} left this backend, so a backend they reach soon after keeps them hidden. */
    public void departed(UUID who) {
        Objects.requireNonNull(who, "who");
        Instant at = clock.instant();
        scheduler.async(() -> {
            try {
                records.markDeparted(who, at);
            } catch (RuntimeException failed) {
                // Only a server switch in the next moments is affected: the player then arrives visible.
                log.warn("event=vanish_departure_save_failed player={} reason={}", who, failed.toString());
            }
        });
    }

    /**
     * Clear a kept vanish that did not carry over to this join, so it cannot come back later. Unlike a reveal this is
     * not a change the player made here.
     */
    public void clearStored(UUID who) {
        Objects.requireNonNull(who, "who");
        write(who, StoredVanish.visible(clock.instant()));
    }

    /** The kept state of {@code who}. Blocks on the database, so only an off-tick caller reads it. */
    public Optional<StoredVanish> load(UUID who) {
        Objects.requireNonNull(who, "who");
        return records.find(who);
    }

    /** Whether {@code who} changed their vanish on this backend since they joined it. */
    public boolean changedHere(UUID who) {
        Objects.requireNonNull(who, "who");
        return changedHere.contains(who);
    }

    /** Drop what this backend remembers of {@code who} once they leave it; the durable row stays. */
    public void forget(UUID who) {
        Objects.requireNonNull(who, "who");
        changedHere.remove(who);
    }

    private void save(UUID who, StoredVanish state) {
        Objects.requireNonNull(who, "who");
        changedHere.add(who);
        write(who, state);
    }

    private void write(UUID who, StoredVanish state) {
        scheduler.async(() -> {
            try {
                records.save(who, state);
            } catch (RuntimeException failed) {
                // The live state and the bus frame already moved; only the next session loses this change.
                log.warn("event=vanish_save_failed player={} reason={}", who, failed.toString());
            }
        });
    }
}
