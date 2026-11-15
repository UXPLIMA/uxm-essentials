package com.uxplima.uxmessentials.vanish.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
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
        Instant at = clock.instant();
        run(who, "arrival", () -> records.markArrived(who, server, at));
    }

    /**
     * Confirm that the vanished {@code online} players are still on this backend. Called on a timer well inside the hop
     * window, so a reader can tell them from players whose backend went down without writing a departure.
     */
    public void refresh(Collection<UUID> online) {
        Objects.requireNonNull(online, "online");
        if (online.isEmpty()) {
            return;
        }
        Set<UUID> players = Set.copyOf(online);
        Instant at = clock.instant();
        scheduler.async(() -> {
            try {
                records.touch(players, server, at);
            } catch (RuntimeException failed) {
                log.warn(
                        "event=vanish_write_failed kind=sighting players={} reason={}",
                        players.size(),
                        failed.toString());
            }
        });
    }

    /**
     * Record a departure for every vanished player this backend holds a row for from before now: what a run of it that
     * went down without writing departures left behind. Called on start; a player who arrives while it runs was seen
     * after this instant and keeps their row.
     */
    public void releaseOrphans() {
        Instant startedAt = clock.instant();
        scheduler.async(() -> release("start", () -> records.releaseStale(server, startedAt)));
    }

    /**
     * Record a departure for every vanished player this backend holds, on the calling thread, in one statement. Called on
     * stop, when the async lane no longer runs and the server disables plugins before it lets the players go, so no quit
     * is seen.
     */
    public void releaseAllNow() {
        Instant at = clock.instant();
        release("stop", () -> records.releaseHeld(server, at));
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

    private void release(String when, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException failed) {
            // The rows keep a sighting that ages out within the hop window, so no player stays hidden for long.
            log.warn(
                    "event=vanish_write_failed kind=release when={} server={} reason={}",
                    when,
                    server,
                    failed.toString());
        }
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
