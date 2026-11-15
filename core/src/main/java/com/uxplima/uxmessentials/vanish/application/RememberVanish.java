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
 * a vanish survives a server switch, and how a player who may persist it comes back hidden after a restart. Every
 * write names this backend, so the next one can tell where the player was. A write is stamped when the change happens
 * and runs on the async lane, so a command never waits on the database and two writes that finish out of order still
 * settle on the later change.
 *
 * <p>It also remembers which online players changed their vanish on this backend, so a stored state that arrives late
 * (the login read timed out) never overrides a change the player made after joining.
 */
public final class RememberVanish {

    private final VanishRecords records;
    private final String server;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Logger log;
    private final Set<UUID> changedHere = ConcurrentHashMap.newKeySet();

    public RememberVanish(VanishRecords records, String server, Scheduler scheduler, Clock clock, Logger log) {
        this.records = Objects.requireNonNull(records, "records");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Keep {@code who} as vanished at {@code level} on this backend. */
    public void vanished(UUID who, VanishLevel level) {
        save(who, StoredVanish.vanished(level, server, clock.instant()));
    }

    /** Keep {@code who} as visible. */
    public void revealed(UUID who) {
        save(who, StoredVanish.visible(clock.instant()));
    }

    /** Record that the vanished {@code who} left this backend, so a backend they reach soon after keeps them hidden. */
    public void departed(UUID who) {
        Objects.requireNonNull(who, "who");
        Instant at = clock.instant();
        run(who, "departure", () -> records.markDeparted(who, server, at));
    }

    /** Record that the vanished {@code who} is now on this backend, so a late departure from the last one is ignored. */
    public void arrived(UUID who) {
        Objects.requireNonNull(who, "who");
        run(who, "arrival", () -> records.markArrived(who, server));
    }

    /**
     * Clear a kept vanish that did not carry over to this join, so it cannot come back later. Unlike a reveal this is
     * not a change the player made here.
     */
    public void clearStored(UUID who) {
        Objects.requireNonNull(who, "who");
        StoredVanish visible = StoredVanish.visible(clock.instant());
        run(who, "clear", () -> records.save(who, visible));
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
        run(who, "save", () -> records.save(who, state));
    }

    private void run(UUID who, String what, Runnable write) {
        scheduler.async(() -> {
            try {
                write.run();
            } catch (RuntimeException failed) {
                // The live state and the bus frames already moved; only the next backend's read can be off.
                log.warn("event=vanish_write_failed kind={} player={} reason={}", what, who, failed.toString());
            }
        });
    }
}
