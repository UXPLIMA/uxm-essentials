package com.uxplima.uxmessentials.vanish.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The live {@link NetworkVanishStore}: who each peer reports vanished, keyed by player, with the reporting backend and
 * when the report arrived here. Every time is this backend's own clock at arrival, so clock skew between backends
 * never decides anything.
 *
 * <ul>
 *   <li>An entry counts while {@link NetworkPresence} says its backend is running. A report from a backend presence has
 *       not heard of yet still counts for one hop window, since a backend that just started can report a vanish before
 *       its first heartbeat arrives.
 *   <li>A departure turns the entry into a tombstone that hides an arriving player for one hop window and is never
 *       listed.
 *   <li>The first heartbeat of a backend's new run drops what arrived before its previous heartbeat, which can only be
 *       the old run's; what the new run reported before its first heartbeat is kept.
 * </ul>
 *
 * <p>Reads and writes are lock-free map operations, so the bus dispatch thread and a command thread see a coherent
 * value.
 */
public final class NetworkVanishRoster implements NetworkVanishStore {

    private record Entry(String origin, String name, VanishLevel level, Instant seen, Optional<Instant> departed) {}

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    private final NetworkPresence presence;
    private final Clock clock;
    private final Duration hopWindow;

    public NetworkVanishRoster(NetworkPresence presence, Clock clock, Duration hopWindow) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hopWindow = Objects.requireNonNull(hopWindow, "hopWindow");
    }

    @Override
    public void apply(String origin, VanishSync change) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(change, "change");
        if (change.vanished()) {
            entries.put(
                    change.player(),
                    new Entry(origin, change.playerName(), change.level(), clock.instant(), Optional.empty()));
        } else {
            // A reveal is a real /vanish off wherever it came from, so it ends the vanish whoever reported it.
            entries.remove(change.player());
        }
    }

    @Override
    public void depart(String origin, UUID player) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(player, "player");
        Instant now = clock.instant();
        entries.computeIfPresent(
                player,
                (id, entry) -> entry.origin().equals(origin) && entry.departed().isEmpty()
                        ? new Entry(entry.origin(), entry.name(), entry.level(), entry.seen(), Optional.of(now))
                        : entry);
    }

    @Override
    public void heartbeat(String origin, boolean newRun) {
        Objects.requireNonNull(origin, "origin");
        Instant before = lastHeartbeat.put(origin, clock.instant());
        if (newRun && before != null) {
            entries.values()
                    .removeIf(entry ->
                            entry.origin().equals(origin) && !entry.seen().isAfter(before));
        }
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
        entries.clear();
        lastHeartbeat.clear();
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
