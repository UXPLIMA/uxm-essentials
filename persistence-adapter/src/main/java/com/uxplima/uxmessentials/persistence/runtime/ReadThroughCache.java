package com.uxplima.uxmessentials.persistence.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jspecify.annotations.NullMarked;

/**
 * A thin Caffeine read-cache sitting between a repository and the database.
 *
 * <p>Reads go through the cache, which loads a miss from the backing query; writes invalidate the
 * affected key so the next read reflects the durable state (write-through at the repository, invalidate
 * here: never a write-back cache that could lose a mutation). The cache is bounded by size and a short
 * expire-after-write so a never-touched-again entry cannot pin memory; the source of truth is always the
 * DB. This is the shared shape the per-context cache decorators ({@code CachedHomeRepository},
 * {@code CachedWarpRepository}) compose rather than re-implementing Caffeine wiring each time.
 *
 * @param <K> the cache key (a home owner, a warp name)
 * @param <V> the cached value (the loaded aggregate)
 */
@NullMarked
public final class ReadThroughCache<K, V> {

    private final LoadingCache<K, V> cache;

    private ReadThroughCache(LoadingCache<K, V> cache) {
        this.cache = cache;
    }

    /**
     * Build a cache that loads a miss via {@code loader}, bounded to {@code maximumSize} entries and
     * expiring each entry {@code expireAfterWrite} after it was loaded or refreshed.
     */
    public static <K, V> ReadThroughCache<K, V> create(
            Function<K, V> loader, long maximumSize, Duration expireAfterWrite) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(expireAfterWrite, "expireAfterWrite");
        LoadingCache<K, V> cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite)
                .build(loader::apply);
        return new ReadThroughCache<>(cache);
    }

    /** The cached value for {@code key}, loading it from the backing query on a miss. */
    public V get(K key) {
        return cache.get(Objects.requireNonNull(key, "key"));
    }

    /**
     * The cached value for {@code key} only if it is already in memory. Never loads through to the backing
     * query. A present {@link Optional} is a cache hit; an empty one is a miss. Tick-thread callers that must
     * not block on I/O (the home/pwarp name suggesters) read through this rather than {@link #get}.
     */
    public Optional<V> getIfPresent(K key) {
        return Optional.ofNullable(cache.getIfPresent(Objects.requireNonNull(key, "key")));
    }

    /**
     * Put {@code fresh} in place of {@code expected} for {@code key}, only while the entry still holds
     * {@code expected}. A write that invalidated the key, or a read that reloaded it, since {@code expected} was read
     * makes this a no-op, so a value loaded before that write never overwrites what it wrote. For a refresh off the
     * tick that must leave no gap where a reader would load on its own thread.
     *
     * @return whether the entry now holds {@code fresh}
     */
    public boolean replace(K key, V expected, V fresh) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(fresh, "fresh");
        return cache.asMap().replace(key, expected, fresh);
    }

    /** Drop {@code key} so the next read reloads it; call after a write that changed it. */
    public void invalidate(K key) {
        cache.invalidate(Objects.requireNonNull(key, "key"));
    }

    /** Drop every entry; call on a bulk change or a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
