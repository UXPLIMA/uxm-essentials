package com.uxplima.uxmessentials.persistence.warps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;

/**
 * An in-memory-authoritative decorator over a delegate {@link WarpCategoryRepository}, the category twin of
 * {@link CachedWarpRepository}. Warp categories are server-wide and the set is small and bounded, and it is
 * read on every browse-menu open and every category-GUI render on the command/region thread, so re-querying
 * SQLite on a miss would block that thread. The set is loaded once (lazily on the first read, or via the
 * wiring's warm load on enable) and thereafter is the authoritative answer for {@code find}/{@code all}.
 *
 * <p>Every mutation goes through this repository, so the set stays complete: a {@code save} writes through to
 * the delegate then publishes a set with the category added/replaced (immediately findable), and a
 * {@code delete} writes through then publishes one with it removed. The set is held in an
 * {@link AtomicReference} and swapped copy-on-write so a reader always sees a consistent immutable snapshot
 * while a writer publishes the next one. The durable source of truth is always the delegate; the set is
 * rebuilt from it on the next read after an {@link #invalidateAll()} (a module reload, or a cross-server peer
 * reporting a change).
 */
public final class CachedWarpCategoryRepository implements WarpCategoryRepository {

    private final WarpCategoryRepository delegate;
    /** The loaded id → category set, or {@code null} until the first load warms it (lazily, or via the wiring). */
    private final AtomicReference<Map<String, WarpCategory>> loaded = new AtomicReference<>();

    public CachedWarpCategoryRepository(WarpCategoryRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<WarpCategory> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(index().get(id));
    }

    @Override
    public List<WarpCategory> all() {
        return List.copyOf(index().values());
    }

    @Override
    public void save(WarpCategory category) {
        Objects.requireNonNull(category, "category");
        delegate.save(category);
        republish(next -> next.put(category.id(), category));
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        delegate.delete(id);
        republish(next -> next.remove(id));
    }

    /** Drop the loaded set so the next read rebuilds it from the delegate; call on a module reload or peer sync. */
    public void invalidateAll() {
        loaded.set(null);
    }

    private Map<String, WarpCategory> index() {
        Map<String, WarpCategory> current = loaded.get();
        if (current != null) {
            return current;
        }
        Map<String, WarpCategory> fresh = loadAll();
        if (loaded.compareAndSet(null, fresh)) {
            return fresh;
        }
        Map<String, WarpCategory> winner = loaded.get();
        return winner != null ? winner : fresh;
    }

    private void republish(Consumer<Map<String, WarpCategory>> edit) {
        while (true) {
            Map<String, WarpCategory> current = index();
            Map<String, WarpCategory> next = new LinkedHashMap<>(current);
            edit.accept(next);
            if (loaded.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private Map<String, WarpCategory> loadAll() {
        Map<String, WarpCategory> byId = new LinkedHashMap<>();
        for (WarpCategory category : delegate.all()) {
            byId.put(category.id(), category);
        }
        return byId;
    }
}
