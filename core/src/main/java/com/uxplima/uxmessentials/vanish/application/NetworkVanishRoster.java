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

import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.PeerLiveness;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The live {@link NetworkVanishStore}: who each peer reports vanished, keyed by player, with the reporting backend and
 * when the report arrived. An entry counts while its backend is live in the heartbeat roster, so a backend that
 * crashed, and so could not say its players left, ages out of the view without help.
 *
 * <p>A report that arrives before its backend's first heartbeat is kept for one grace window anyway: a backend that
 * just started can report a vanish before the roster has heard from it.
 *
 * <p>Reads and writes are lock-free map operations, so the bus dispatch thread and a command thread see a coherent
 * value.
 */
public final class NetworkVanishRoster implements NetworkVanishStore {

    private record Entry(String origin, String name, VanishLevel level, Instant seen) {}

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final PeerLiveness liveness;
    private final Clock clock;
    private final Duration grace;

    public NetworkVanishRoster(PeerLiveness liveness, Clock clock, Duration grace) {
        this.liveness = Objects.requireNonNull(liveness, "liveness");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.grace = Objects.requireNonNull(grace, "grace");
    }

    @Override
    public void apply(String origin, VanishSync change) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(change, "change");
        if (change.vanished()) {
            entries.put(change.player(), new Entry(origin, change.playerName(), change.level(), clock.instant()));
        } else {
            // A reveal is a real /vanish off wherever it came from, so it ends the vanish whoever reported it.
            entries.remove(change.player());
        }
    }

    @Override
    public void depart(String origin, UUID player) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(player, "player");
        entries.computeIfPresent(player, (id, entry) -> entry.origin().equals(origin) ? null : entry);
    }

    @Override
    public void forgetServer(String origin) {
        Objects.requireNonNull(origin, "origin");
        entries.values().removeIf(entry -> entry.origin().equals(origin));
    }

    @Override
    public Optional<VanishLevel> levelOf(UUID who) {
        return live(who).map(Entry::level);
    }

    @Override
    public Optional<String> nameOf(UUID who) {
        return live(who).map(Entry::name);
    }

    @Override
    public Map<UUID, VanishLevel> levels() {
        Map<UUID, VanishLevel> copy = new HashMap<>();
        entries.forEach((id, entry) -> {
            if (alive(entry)) {
                copy.put(id, entry.level());
            } else {
                entries.remove(id, entry);
            }
        });
        return Map.copyOf(copy);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    private Optional<Entry> live(UUID who) {
        Objects.requireNonNull(who, "who");
        Entry entry = entries.get(who);
        if (entry == null) {
            return Optional.empty();
        }
        if (alive(entry)) {
            return Optional.of(entry);
        }
        entries.remove(who, entry);
        return Optional.empty();
    }

    private boolean alive(Entry entry) {
        return liveness.isLive(entry.origin())
                || !clock.instant().isAfter(entry.seen().plus(grace));
    }
}
