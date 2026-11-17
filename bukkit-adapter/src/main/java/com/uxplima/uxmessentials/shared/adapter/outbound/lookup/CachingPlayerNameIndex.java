package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
 * The {@link PlayerNameIndex} implementation: every account under its lower-cased name, warmed once from the
 * database at enable and kept current by the join listener.
 *
 * <p>A lower-cased name holds every account seen under it, not just the last: on an offline-mode server
 * {@code Steve} and {@code steve} are two accounts, and which one a typed name means is the lookup's to decide. An
 * account holds one name, the one it was last seen with, so a rename leaves nothing under the old one.
 *
 * <p>The read path is memory-only because it runs inside Brigadier command execution on a tick thread. The
 * database is only touched by {@link #backWith} (once, off-thread, at enable) and by the write-behind of
 * {@link #record}.
 *
 * <p>The kernel is wired before persistence opens, so the index is constructed without a repository and
 * {@link #backWith} attaches one afterwards. Until then the index is a pure in-memory map, which is harmless: no
 * player can have joined yet.
 *
 * <p>The index holds the {@code warmLimit} most recently seen accounts and lets the least recently seen one go when a
 * new one arrives past it. The window bounds the live map as well as the warm, so a long-running server does
 * not hold every name it has ever seen.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>lock</b>. The accounts are a {@link LinkedHashMap} in the order they were last seen, with a map from
 * lower-cased name to the accounts under it beside it; every read and write of the two is one short critical section
 * on {@code lock}. The repository reference is an {@link AtomicReference} written once at enable. No database call is made inside a critical section.
 */
@NullMarked
public final class CachingPlayerNameIndex implements PlayerNameIndex {

    private final Object lock = new Object();
    private final LinkedHashMap<UUID, PlayerName> byAccount = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> byLowerName = new HashMap<>();

    /** How many accounts are held. Unbounded until the store is attached, before which nobody can have joined. */
    private volatile int limit = Integer.MAX_VALUE;

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
        limit = Math.max(1, warmLimit);
        List<PlayerName> recent = store.loadRecent(warmLimit);
        // Oldest first, so the most recently seen accounts are the last the window lets go.
        for (int i = recent.size() - 1; i >= 0; i--) {
            remember(recent.get(i));
        }
        log.info("event=name_index_warmed rows={}", String.valueOf(recent.size()));
    }

    @Override
    public List<PlayerRef> byName(String name) {
        Objects.requireNonNull(name, "name");
        String key = name.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return List.of();
        }
        List<PlayerName> found = new ArrayList<>();
        synchronized (lock) {
            for (UUID account : byLowerName.getOrDefault(key, Set.of())) {
                PlayerName row = byAccount.get(account);
                if (row != null) {
                    found.add(row);
                }
            }
        }
        // Remembered last first, so of two seen in the same millisecond the later one leads; the sort keeps that.
        Collections.reverse(found);
        found.sort(Comparator.comparingLong(PlayerName::lastSeen).reversed());
        return found.stream().map(PlayerName::ref).toList();
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
        remember(row);
        PlayerNameRepository store = repository.get();
        if (store == null) {
            return;
        }
        scheduler.async(() -> store.upsert(row));
    }

    @Override
    public void learn(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        remember(new PlayerName(uuid, trimmed, System.currentTimeMillis()));
    }

    /**
     * Put an account last in the order seen under the name it now has, and let the least recently seen go past the
     * limit.
     */
    private void remember(PlayerName row) {
        synchronized (lock) {
            PlayerName previous = byAccount.remove(row.uuid());
            if (previous != null) {
                unlink(previous);
            }
            byAccount.put(row.uuid(), row);
            byLowerName
                    .computeIfAbsent(key(row.name()), name -> new LinkedHashSet<>())
                    .add(row.uuid());
            Iterator<PlayerName> oldest = byAccount.values().iterator();
            while (byAccount.size() > limit && oldest.hasNext()) {
                PlayerName gone = oldest.next();
                oldest.remove();
                unlink(gone);
            }
        }
    }

    /** Take an account out from under the name it had. Called with {@code lock} held. */
    private void unlink(PlayerName row) {
        String key = key(row.name());
        Set<UUID> accounts = byLowerName.get(key);
        if (accounts == null) {
            return;
        }
        accounts.remove(row.uuid());
        if (accounts.isEmpty()) {
            byLowerName.remove(key);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
