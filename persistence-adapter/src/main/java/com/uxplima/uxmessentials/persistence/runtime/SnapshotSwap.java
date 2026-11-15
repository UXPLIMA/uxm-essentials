package com.uxplima.uxmessentials.persistence.runtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jspecify.annotations.NullMarked;

/**
 * One whole-set reload of a copy-on-write cache: the set it replaced and the set loaded from the database.
 *
 * <p>{@link #reload} is how the set caches ({@code CachedWarpRepository}, {@code CachedNpcRepository} and the rest)
 * take in rows another server wrote without ever leaving the reference empty. Readers keep the old set while the
 * fresh one loads, so none of them falls back to a synchronous load on a tick thread. The swap is a compare-and-set
 * against the set read before the load, so a local write that published in the meantime makes it fail and the write
 * survives; the next reload takes it in.
 *
 * @param before the set the reload replaced
 * @param after the set now published
 * @param <V> the cached value
 */
@NullMarked
public record SnapshotSwap<V>(Map<String, V> before, Map<String, V> after) {

    public SnapshotSwap {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    /**
     * Load the set with {@code load} and swap it into {@code ref} if no write published meanwhile. Empty when the
     * set was never loaded (there is nothing stale to replace, so the fresh set is published as the first one) or
     * when a local write overtook the load twice in a row.
     */
    public static <V> Optional<SnapshotSwap<V>> reload(
            AtomicReference<Map<String, V>> ref, Supplier<Map<String, V>> load) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(load, "load");
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<String, V> current = ref.get();
            Map<String, V> fresh = load.get();
            if (current == null) {
                ref.compareAndSet(null, fresh);
                return Optional.empty();
            }
            if (ref.compareAndSet(current, fresh)) {
                return Optional.of(new SnapshotSwap<>(current, fresh));
            }
        }
        return Optional.empty();
    }

    /** The keys added, removed or holding a different value after the swap. */
    public Set<String> changedKeys() {
        Set<String> changed = new HashSet<>();
        after.forEach((key, value) -> {
            if (!value.equals(before.get(key))) {
                changed.add(key);
            }
        });
        for (String key : before.keySet()) {
            if (!after.containsKey(key)) {
                changed.add(key);
            }
        }
        return changed;
    }
}
