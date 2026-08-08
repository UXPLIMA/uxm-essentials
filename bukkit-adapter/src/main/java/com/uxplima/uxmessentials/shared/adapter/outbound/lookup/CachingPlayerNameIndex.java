package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link PlayerNameIndex} implementation: a lower-cased-name to account map, warmed once from the database at
 * enable and kept current by the join listener.
 *
 * <p>The read path is memory-only because it runs inside Brigadier command execution on a tick thread. The
 * database is only touched by {@link #backWith} (once, off-thread, at enable) and by the write-behind of
 * {@link #record}.
 *
 * <p>The kernel is wired before persistence opens, so the index is constructed without a repository and
 * {@link #backWith} attaches one afterwards. Until then the index is a pure in-memory map, which is harmless: no
 * player can have joined yet.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The map is a {@link ConcurrentHashMap} keyed by the lower-cased name;
 * the repository reference is an {@link AtomicReference} written once at enable. No database call is made inside a
 * critical section.
 */
@NullMarked
public final class CachingPlayerNameIndex implements PlayerNameIndex {

    private final ConcurrentHashMap<String, PlayerName> byLowerName = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable PlayerNameRepository> repository = new AtomicReference<>();
    private final Scheduler scheduler;
    private final Logger log;

    public CachingPlayerNameIndex(Scheduler scheduler, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Attach the durable store and warm the map with its {@code warmLimit} most recently seen rows. Reads the
     * database, so the caller runs it off the tick thread.
     */
    public void backWith(PlayerNameRepository store, int warmLimit) {
        Objects.requireNonNull(store, "store");
        repository.set(store);
        List<PlayerName> recent = store.loadRecent(warmLimit);
        // Oldest first, so a newer row for the same lower-cased name overwrites an older one and the last write
        // wins. Two accounts can share a lower-cased name: a name change on an online-mode server, two case
        // variants of one name on an offline-mode one.
        for (int i = recent.size() - 1; i >= 0; i--) {
            PlayerName row = recent.get(i);
            byLowerName.put(row.name().toLowerCase(Locale.ROOT), row);
        }
        log.info("event=name_index_warmed rows={}", String.valueOf(recent.size()));
    }

    @Override
    public Optional<PlayerRef> byName(String name) {
        Objects.requireNonNull(name, "name");
        String key = name.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        PlayerName found = byLowerName.get(key);
        return found == null ? Optional.empty() : Optional.of(found.ref());
    }

    @Override
    public void record(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        PlayerName row = new PlayerName(uuid, trimmed, System.currentTimeMillis());
        byLowerName.put(trimmed.toLowerCase(Locale.ROOT), row);
        PlayerNameRepository store = repository.get();
        if (store == null) {
            return;
        }
        scheduler.async(() -> store.upsert(row));
    }
}
