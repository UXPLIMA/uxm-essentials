package com.uxplima.uxmessentials.vanish.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The live {@link NetworkVanishStore}: who each peer reports vanished, keyed by player, with the reporting backend, the
 * run of that backend that sent the report, and when the report arrived here. Every time is this backend's own clock at
 * arrival, so clock skew between backends never decides anything.
 *
 * <ul>
 *   <li>An entry counts while {@link NetworkPresence} says its backend is running. A report from a backend presence has
 *       not heard of yet still counts for one hop window, since a backend that just started can report a vanish before
 *       its first heartbeat arrives.
 *   <li>A departure turns the entry into a tombstone that hides an arriving player for one hop window and is never
 *       listed.
 *   <li>The first heartbeat of a backend's new run drops every entry another run of it sent, whenever that arrived, and
 *       a late frame from an ended run is ignored; what the new run reported before its first heartbeat is kept. A peer
 *       that names no run on its frames ({@link #UNKNOWN_RUN}) still loses its old entries on a restart, but its frames
 *       are never ignored.
 *   <li>Frames of one run are numbered, and the newest number applied for each player and origin is kept, even after a
 *       reveal removed the entry. A change or departure no newer than that is ignored, so a frame delivered late, out
 *       of order or twice cannot undo a newer one. A frame that names no number is applied as it arrives.
 * </ul>
 *
 * <p>Reads are lock-free map operations, so a command thread never waits on the bus. Writes take the roster's lock, so
 * the check against the newest number and the change it allows happen as one step even when frames are dispatched
 * on several threads at once.
 */
public final class NetworkVanishRoster implements NetworkVanishStore {

    private record Entry(
            String origin, long run, String name, VanishLevel level, Instant seen, Optional<Instant> departed) {}

    /** One player as one origin reports them. */
    private record Source(UUID player, String origin) {}

    /** The newest frame applied from a source: its run and its number within that run. */
    private record Newest(long run, long sequence) {}

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> endedRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> currentRun = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Source, Newest> newest = new ConcurrentHashMap<>();
    private final Object writes = new Object();
    private final NetworkPresence presence;
    private final Clock clock;
    private final Duration hopWindow;

    public NetworkVanishRoster(NetworkPresence presence, Clock clock, Duration hopWindow) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hopWindow = Objects.requireNonNull(hopWindow, "hopWindow");
    }

    @Override
    public boolean apply(String origin, long run, long sequence, VanishSync change) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(change, "change");
        synchronized (writes) {
            if (ended(origin, run) || !newer(change.player(), origin, run, sequence)) {
                return false;
            }
            if (change.vanished()) {
                entries.put(
                        change.player(),
                        new Entry(origin, run, change.playerName(), change.level(), clock.instant(), Optional.empty()));
            } else {
                // A reveal is a real /vanish off wherever it came from, so it ends the vanish whoever reported it.
                entries.remove(change.player());
            }
            return true;
        }
    }

    @Override
    public void depart(String origin, long run, long sequence, UUID player) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(player, "player");
        synchronized (writes) {
            if (newer(player, origin, run, sequence)) {
                markDeparted(origin, player);
            }
        }
    }

    private void markDeparted(String origin, UUID player) {
        Instant now = clock.instant();
        entries.computeIfPresent(
                player,
                (id, entry) -> entry.origin().equals(origin) && entry.departed().isEmpty()
                        ? new Entry(
                                entry.origin(),
                                entry.run(),
                                entry.name(),
                                entry.level(),
                                entry.seen(),
                                Optional.of(now))
                        : entry);
    }

    @Override
    public void heartbeat(String origin, long run, boolean newRun) {
        Objects.requireNonNull(origin, "origin");
        synchronized (writes) {
            beat(origin, run, newRun);
        }
    }

    private void beat(String origin, long run, boolean newRun) {
        Long before = currentRun.put(origin, run);
        if (!newRun) {
            return;
        }
        Set<Long> ended = endedRuns.computeIfAbsent(origin, key -> ConcurrentHashMap.newKeySet());
        if (before != null && before != run && before != UNKNOWN_RUN) {
            ended.add(before);
        }
        entries.forEach((id, entry) -> {
            if (entry.origin().equals(origin) && entry.run() != run) {
                // A peer that names no run on its frames still loses what it reported before it restarted.
                if (entry.run() != UNKNOWN_RUN) {
                    ended.add(entry.run());
                }
                entries.remove(id, entry);
            }
        });
        // The numbers of an ended run order nothing any more; the new run counts from its own start.
        newest.entrySet()
                .removeIf(last ->
                        last.getKey().origin().equals(origin) && last.getValue().run() != run);
    }

    @Override
    public Optional<VanishLevel> levelOf(UUID who) {
        return current(who).map(Entry::level);
    }

    @Override
    public Optional<String> nameOf(UUID who) {
        return current(who).filter(entry -> entry.departed().isEmpty()).map(Entry::name);
    }

    @Override
    public Map<UUID, VanishLevel> levels() {
        Map<UUID, VanishLevel> copy = new HashMap<>();
        entries.forEach((id, entry) -> {
            if (!counts(entry)) {
                entries.remove(id, entry);
            } else if (entry.departed().isEmpty()) {
                copy.put(id, entry.level());
            }
        });
        return Map.copyOf(copy);
    }

    @Override
    public void clear() {
        synchronized (writes) {
            entries.clear();
            endedRuns.clear();
            currentRun.clear();
            newest.clear();
        }
    }

    /**
     * Whether a frame is newer than the last one applied about {@code player} from the same origin and run, recording
     * it as the newest when it is. A frame without a run or a number cannot be ordered, so it always counts as newer;
     * one from another run than the last replaces it, since an ended run's frames were already turned away.
     */
    private boolean newer(UUID player, String origin, long run, long sequence) {
        if (run == UNKNOWN_RUN || sequence == UNKNOWN_SEQUENCE) {
            return true;
        }
        Source source = new Source(player, origin);
        Newest last = newest.get(source);
        if (last != null && last.run() == run && last.sequence() >= sequence) {
            return false;
        }
        newest.put(source, new Newest(run, sequence));
        return true;
    }

    private boolean ended(String origin, long run) {
        if (run == UNKNOWN_RUN) {
            return false;
        }
        Set<Long> runs = endedRuns.get(origin);
        return runs != null && runs.contains(run);
    }

    private Optional<Entry> current(UUID who) {
        Objects.requireNonNull(who, "who");
        Entry entry = entries.get(who);
        if (entry == null) {
            return Optional.empty();
        }
        if (counts(entry)) {
            return Optional.of(entry);
        }
        entries.remove(who, entry);
        return Optional.empty();
    }

    private boolean counts(Entry entry) {
        Instant now = clock.instant();
        Optional<Instant> departed = entry.departed();
        if (departed.isPresent()) {
            return !now.isAfter(departed.get().plus(hopWindow));
        }
        return presence.isLive(entry.origin()) || !now.isAfter(entry.seen().plus(hopWindow));
    }
}
