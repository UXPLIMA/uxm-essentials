package com.uxplima.uxmessentials.shared.adapter.outbound.playerdata;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.PlayerDataRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * A {@link PlayerDataStore} that fronts the database-backed {@link PlayerDataRepository} with an in-memory cache so
 * the menu engine's reads are entity-thread-safe and its writes never block the tick thread on the database.
 *
 * <p>A player's whole row set is loaded once into the cache on join ({@link #load}, called off the tick thread by
 * the lifecycle listener) and dropped on quit ({@link #evict}). Thereafter every read ({@link #get},
 * {@link #number}, {@link #all}) is a pure cache hit, and every write ({@link #set}, {@link #apply},
 * {@link #remove}) mutates the cache synchronously and then schedules the matching database write through the
 * {@link Scheduler} port's {@code async} seam. The persisted value is the one captured at write time, so an
 * in-flight write that runs after the player has been evicted still stores the correct row, and the player's next
 * join re-reads it from the database.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The outer map is keyed by player and each inner map is a
 * {@link ConcurrentHashMap}; {@link #apply} performs its read-modify-write atomically per key via
 * {@code compute}. The repository call is enqueued, not run, inside any critical section, so no database I/O ever
 * happens under a lock. Ordering of two rapid writes to the same key relies on the async executor's FIFO
 * behaviour, which is acceptable for this substrate (player-data is not a hot, high-contention path).
 */
public final class CachingPlayerDataStore implements PlayerDataStore {

    private final PlayerDataRepository repository;
    private final Scheduler scheduler;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> cache = new ConcurrentHashMap<>();

    public CachingPlayerDataStore(PlayerDataRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Warm the player's rows into the cache. Reads the database, so the caller must run it off the tick thread. */
    public void load(UUID player) {
        Objects.requireNonNull(player, "player");
        cache.put(player, new ConcurrentHashMap<>(repository.loadAll(player)));
    }

    /** Drop the player's cached rows (on quit). In-flight async writes have already captured their values. */
    public void evict(UUID player) {
        Objects.requireNonNull(player, "player");
        cache.remove(player);
    }

    @Override
    public Optional<String> get(UUID player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        ConcurrentHashMap<String, String> map = cache.get(player);
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(key));
    }

    @Override
    public double number(UUID player, String key, double fallback) {
        return parse(get(player, key).orElse(null), fallback);
    }

    @Override
    public void set(UUID player, String key, String value) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        forWrite(player).put(key, value);
        persist(player, key, value);
    }

    @Override
    public double apply(UUID player, String key, NumericOp op, double operand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(op, "op");
        if (op == NumericOp.DIV && operand == 0.0) {
            return number(player, key, 0.0); // documented no-change: leave the stored value, return the current one
        }
        double[] result = new double[1];
        forWrite(player).compute(key, (k, current) -> {
            double base = parse(current, 0.0);
            double next =
                    switch (op) {
                        case SET -> operand;
                        case ADD -> base + operand;
                        case SUB -> base - operand;
                        case MUL -> base * operand;
                        case DIV -> base / operand;
                    };
            result[0] = next;
            return format(next);
        });
        persist(player, key, format(result[0]));
        return result[0];
    }

    @Override
    public void remove(UUID player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        ConcurrentHashMap<String, String> map = cache.get(player);
        if (map != null) {
            map.remove(key);
        }
        scheduler.async(() -> repository.delete(player, key));
    }

    @Override
    public Map<String, String> all(UUID player) {
        Objects.requireNonNull(player, "player");
        ConcurrentHashMap<String, String> map = cache.get(player);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    /** The player's cache map, created empty if the player has not been loaded, so a write always has somewhere to go. */
    private ConcurrentHashMap<String, String> forWrite(UUID player) {
        return cache.computeIfAbsent(player, p -> new ConcurrentHashMap<>());
    }

    /** Schedule the durable write off the tick thread; the cache already holds the new value for reads. */
    private void persist(UUID player, String key, String value) {
        scheduler.async(() -> repository.upsert(player, key, value));
    }

    private static double parse(@Nullable String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException notNumeric) {
            return fallback;
        }
    }

    /** Render a whole number without a trailing {@code .0} so counters read as {@code 5} rather than {@code 5.0}. */
    private static String format(double value) {
        if (Double.isFinite(value) && value == Math.floor(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
