package com.uxplima.uxmessentials.ranks.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * An in-memory cache over a delegate {@link PlayerRankRepository} holding the rank pointer of every online player,
 * so the reads that run on a tick thread (placeholders on a scoreboard, the ladder panel, a rankup's standing) never
 * reach the database.
 *
 * <p>A player's pointer is loaded while they log in ({@link #preload}, which waits a bounded time on the pre-login
 * thread), with {@link #join} as the fallback that loads on the async lane, and dropped when they leave or their
 * connection closes before they joined ({@link #quit}). The
 * cache is therefore bounded by the online set and needs no expiry. A {@link #save} writes through to the delegate
 * first and only then updates memory, so a failed write leaves the cached pointer at what the database still holds.
 * {@link #cached} is the tick-safe read: it never blocks, and a miss for an online player schedules a load on the
 * async lane so the next read hits. {@link #find} keeps the port's blocking contract for callers already off the
 * tick.
 *
 * <p><b>Generations.</b> Each online player carries a generation number, renewed when they log in, on every save
 * and on a remote refresh. A load notes the generation when it starts and keeps its result only if the generation is
 * unchanged when it finishes. That drops a read which a local save or a newer refresh overtook, and a read left over
 * from a previous session after a quit and rejoin. The check and the write happen inside one {@code compute} on the
 * generation map, so a save cannot slip between them.
 *
 * <p><b>Saves.</b> Two saves for one player (a rankup and an admin set landing together) run one after the other,
 * so the database and memory end on the same pointer. The lock is striped by player, so it never covers the whole
 * cache and never serializes more than a handful of unrelated saves.
 *
 * <p>It lives in the application layer rather than next to the jOOQ adapter because it is plain Java over two
 * ports, which keeps it testable with fakes.
 */
public final class CachedPlayerRankRepository implements PlayerRankRepository {

    private static final int SAVE_STRIPES = 64;

    private final PlayerRankRepository delegate;
    private final Scheduler scheduler;
    private final Logger log;
    private final AtomicLong generations = new AtomicLong();
    // A player is online here exactly while they have a generation.
    private final ConcurrentMap<UUID, Long> online = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    // Optional values so a loaded player with no stored row is a hit, not a miss that reloads on every read.
    private final ConcurrentMap<UUID, Optional<PlayerRank>> pointers = new ConcurrentHashMap<>();
    private final Object[] saveLocks = new Object[SAVE_STRIPES];

    public CachedPlayerRankRepository(PlayerRankRepository delegate, Scheduler scheduler, Logger log) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        for (int stripe = 0; stripe < SAVE_STRIPES; stripe++) {
            saveLocks[stripe] = new Object();
        }
    }

    /**
     * {@code playerId} is logging in: start a fresh generation, read their pointer on the async lane and wait for it
     * at most {@code wait}, on a thread that may block (the async pre-login event). A slow database never holds the
     * login longer than that: the read finishes later under the same generation, and {@link #join} covers a read
     * that failed.
     */
    public void preload(UUID playerId, Duration wait) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(wait, "wait");
        long generation = renew(playerId);
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        // Marked as loading, so a join or a placeholder read while this read is still out does not start a second.
        boolean marked = loading.add(playerId);
        scheduler.async(() -> {
            try {
                load(playerId, generation);
            } finally {
                if (marked) {
                    loading.remove(playerId);
                }
                loaded.complete(null);
            }
        });
        try {
            loaded.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException slow) {
            log.warn("event=rank_preload_slow player={} wait_ms={}", playerId, wait.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException unreachable) {
            // load() catches its own failures, so the future only ever completes normally.
            log.error("rank pointer preload failed for player " + playerId, unreachable);
        }
    }

    /**
     * {@code playerId} joined. When the pre-login already started their generation it is kept, and only a pointer
     * that failed to load is fetched again; otherwise a fresh generation starts and the load runs on the async lane.
     */
    public void join(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        online.computeIfAbsent(playerId, id -> generations.incrementAndGet());
        if (!pointers.containsKey(playerId)) {
            loadInBackground(playerId);
        }
    }

    /**
     * {@code playerId} left, or their connection closed after the pre-login loaded them but before they joined: forget
     * their pointer so the cache stays bounded by the online set.
     */
    public void quit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        online.compute(playerId, (id, generation) -> {
            pointers.remove(id);
            return null;
        });
    }

    /**
     * Another server changed {@code playerId}'s pointer: re-read it on the async lane when they are online here. A new
     * generation drops any load that started before the change was announced. The old value keeps answering until
     * the fresh one lands, so a placeholder never flickers to empty.
     */
    public void refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Long generation = online.computeIfPresent(playerId, (id, previous) -> generations.incrementAndGet());
        if (generation != null) {
            scheduler.async(() -> load(playerId, generation));
        }
    }

    /** Re-read every online player's pointer on the async lane, for a backend that may have missed remote changes. */
    public void refreshOnline() {
        for (UUID playerId : List.copyOf(online.keySet())) {
            refresh(playerId);
        }
    }

    /**
     * What memory holds for {@code playerId}, never touching the database. The outer {@link Optional} is empty on a
     * miss; the inner one is empty for a loaded player who has no stored pointer yet. A miss for an online player
     * schedules a background load, so the value is there on a later read.
     */
    public Optional<Optional<PlayerRank>> cached(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<PlayerRank> hit = pointers.get(playerId);
        if (hit != null) {
            return Optional.of(hit);
        }
        if (online.containsKey(playerId)) {
            loadInBackground(playerId);
        }
        return Optional.empty();
    }

    /** The pointer from memory when held, otherwise from the delegate. Blocks on a miss; call it off the tick. */
    @Override
    public Optional<PlayerRank> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<PlayerRank> hit = pointers.get(playerId);
        if (hit != null) {
            return hit;
        }
        Long generation = online.get(playerId);
        Optional<PlayerRank> stored = delegate.find(playerId);
        if (generation != null) {
            remember(playerId, generation, stored);
        }
        return stored;
    }

    @Override
    public void save(UUID playerId, RankId rankId, Prestige prestige) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rankId, "rankId");
        Objects.requireNonNull(prestige, "prestige");
        Optional<PlayerRank> saved = Optional.of(new PlayerRank(rankId, prestige));
        synchronized (saveLocks[Math.floorMod(playerId.hashCode(), SAVE_STRIPES)]) {
            delegate.save(playerId, rankId, prestige);
            // A new generation, so any load already reading the old row cannot overwrite what was just written.
            online.computeIfPresent(playerId, (id, generation) -> {
                pointers.put(id, saved);
                return generations.incrementAndGet();
            });
        }
    }

    @Override
    public Map<UUID, Integer> prestiged() {
        return delegate.prestiged();
    }

    private long renew(UUID playerId) {
        long generation = generations.incrementAndGet();
        online.compute(playerId, (id, previous) -> {
            pointers.remove(id);
            return generation;
        });
        return generation;
    }

    private void loadInBackground(UUID playerId) {
        Long generation = online.get(playerId);
        // One load per player at a time: a scoreboard refreshing every tick during a slow join must not queue more.
        if (generation != null && loading.add(playerId)) {
            scheduler.async(() -> {
                try {
                    load(playerId, generation);
                } finally {
                    loading.remove(playerId);
                }
            });
        }
    }

    private void load(UUID playerId, long generation) {
        try {
            remember(playerId, generation, delegate.find(playerId));
        } catch (RuntimeException failure) {
            // Left as a miss, so the next tick-safe read schedules another attempt.
            log.error("failed to load the rank pointer of player " + playerId, failure);
        }
    }

    /** Keep {@code pointer} only while {@code playerId} is still on the generation the read started under. */
    private void remember(UUID playerId, long generation, Optional<PlayerRank> pointer) {
        online.computeIfPresent(playerId, (id, current) -> {
            if (current == generation) {
                pointers.put(id, pointer);
            }
            return current;
        });
    }
}
