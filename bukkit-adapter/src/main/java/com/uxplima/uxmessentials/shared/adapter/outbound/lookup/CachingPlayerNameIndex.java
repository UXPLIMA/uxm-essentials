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
import java.util.Optional;
import java.util.Set;
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

    /**
     * The most accounts read from the store under one name. Case variants of one name are a handful at most; the
     * bound keeps a pathological name from turning a lookup into a table scan.
     */
    private static final int STORED_HOLDERS = 32;

    private final Object lock = new Object();
    private final LinkedHashMap<UUID, PlayerName> byAccount = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> byLowerName = new HashMap<>();
    /**
     * The lower-cased names more than one account holds, read from the store at warm-up and kept current as names are
     * recorded and learned, so the tick can tell a lone holder from one of several without reading the store.
     */
    private final Set<String> sharedNames = ConcurrentHashMap.newKeySet();
    /** Whether {@link #sharedNames} has been read from the store; until then every name may be shared. */
    private volatile boolean warmed;

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
        sharedNames.addAll(store.sharedNames());
        List<PlayerName> recent = store.loadRecent(warmLimit);
        // Oldest first, so the most recently seen accounts are the last the window lets go.
        for (int i = recent.size() - 1; i >= 0; i--) {
            remember(recent.get(i));
        }
        warmed = true;
        log.info("event=name_index_warmed rows={}", String.valueOf(recent.size()));
    }

    /**
     * Read again which names more than one account holds, after rows were added to the store without passing through
     * this index. Reads the database, so the caller runs it off the tick thread.
     */
    public void rereadSharedNames() {
        PlayerNameRepository store = repository.get();
        if (store != null) {
            sharedNames.addAll(store.sharedNames());
        }
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

    /**
     * {@inheritDoc}
     *
     * <p>The store is the word on who holds a name: every backend writes it, while the memory hears only of the joins
     * here and of those the network announces. A remembered holder is kept only where the memory is newer than the
     * store, as for a join whose row is still being written or a name learned from another backend; a holder the
     * store has seen since under another name is let go from under this one, and a newer holder than any remembered
     * is remembered, so the tick answers the same from then on. Most recently seen first.
     */
    @Override
    public List<PlayerRef> storedUnder(String name) {
        Objects.requireNonNull(name, "name");
        PlayerNameRepository store = repository.get();
        if (store == null) {
            return byName(name);
        }
        Map<UUID, PlayerName> holders = new LinkedHashMap<>();
        for (PlayerName row : store.underName(name, STORED_HOLDERS)) {
            holders.putIfAbsent(row.uuid(), row);
        }
        List<PlayerName> remembered = heldUnder(name);
        for (PlayerName held : remembered) {
            PlayerName stored = holders.get(held.uuid());
            if (stored == null) {
                // Not under this name in the store: renamed since on another backend, or its row is not written yet.
                Optional<PlayerName> current = store.byUuid(held.uuid());
                if (current.isPresent() && current.get().lastSeen() >= held.lastSeen()) {
                    rememberIfNewer(current.get());
                    continue;
                }
                holders.put(held.uuid(), held);
            } else if (held.lastSeen() > stored.lastSeen()) {
                holders.put(held.uuid(), held);
            }
        }
        List<PlayerName> found = new ArrayList<>(holders.values());
        found.sort(Comparator.comparingLong(PlayerName::lastSeen).reversed());
        // A newer holder than any the memory has under the name took it: held from now on, so the tick names them.
        // A name the memory holds nobody under stays out of the window, which keeps the accounts seen here lately.
        long newestRemembered =
                remembered.stream().mapToLong(PlayerName::lastSeen).max().orElse(Long.MAX_VALUE);
        if (!found.isEmpty() && found.getFirst().lastSeen() > newestRemembered) {
            rememberIfNewer(found.getFirst());
        }
        return found.stream().map(PlayerName::ref).toList();
    }

    /** The rows the memory holds under {@code name}, in any case. */
    private List<PlayerName> heldUnder(String name) {
        String key = key(name.strip());
        List<PlayerName> found = new ArrayList<>();
        synchronized (lock) {
            for (UUID account : byLowerName.getOrDefault(key, Set.of())) {
                PlayerName row = byAccount.get(account);
                if (row != null) {
                    found.add(row);
                }
            }
        }
        return found;
    }

    @Override
    public Optional<PlayerRef> byUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        synchronized (lock) {
            return Optional.ofNullable(byAccount.get(uuid)).map(PlayerName::ref);
        }
    }

    @Override
    public Optional<PlayerRef> storedByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Optional<PlayerRef> held = byUuid(uuid);
        PlayerNameRepository store = repository.get();
        if (held.isPresent() || store == null) {
            return held;
        }
        Optional<PlayerName> stored = store.byUuid(uuid);
        // Read once, held from now on: a list shown again, on the tick too, names the account from memory.
        stored.ifPresent(this::rememberIfAbsent);
        return stored.map(PlayerName::ref);
    }

    @Override
    public boolean shared(String name) {
        Objects.requireNonNull(name, "name");
        String key = key(name.strip());
        // Before the store has said which names have several holders, a lone holder in memory proves nothing.
        if (!warmed || sharedNames.contains(key)) {
            return true;
        }
        synchronized (lock) {
            return byLowerName.getOrDefault(key, Set.of()).size() > 1;
        }
    }

    @Override
    public void joining(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        PlayerNameRepository store = repository.get();
        String trimmed = name.strip();
        if (store != null && !trimmed.isEmpty()) {
            noteIfShared(store, uuid, trimmed);
        }
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
        scheduler.async(() -> {
            store.upsert(row);
            noteIfShared(store, uuid, trimmed);
        });
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
        PlayerNameRepository store = repository.get();
        if (store != null) {
            // The backend the player joined writes the row, but another holder of the name may be known only there.
            scheduler.async(() -> noteIfShared(store, uuid, trimmed));
        }
    }

    /**
     * Mark {@code name} shared when the store holds another account under it, one the memory may have let go. Reads
     * the store, so it runs off the tick.
     */
    private void noteIfShared(PlayerNameRepository store, UUID uuid, String name) {
        for (PlayerName row : store.underName(name, 2)) {
            if (!row.uuid().equals(uuid)) {
                sharedNames.add(key(name));
                return;
            }
        }
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
            Set<UUID> holders = byLowerName.computeIfAbsent(key(row.name()), name -> new LinkedHashSet<>());
            holders.add(row.uuid());
            if (holders.size() > 1) {
                sharedNames.add(key(row.name()));
            }
            Iterator<PlayerName> oldest = byAccount.values().iterator();
            while (byAccount.size() > limit && oldest.hasNext()) {
                PlayerName gone = oldest.next();
                oldest.remove();
                unlink(gone);
            }
        }
    }

    /**
     * Hold a row read from the store, unless the memory already has a newer word on the account (a join or a learned
     * name since the read began).
     */
    private void rememberIfAbsent(PlayerName row) {
        synchronized (lock) {
            if (byAccount.containsKey(row.uuid())) {
                return;
            }
            remember(row);
        }
    }

    /**
     * Hold a row read from the store unless the memory already has a word on the account at least as new, so a read
     * never undoes a join or a learned name that landed while it ran.
     */
    private void rememberIfNewer(PlayerName row) {
        synchronized (lock) {
            PlayerName held = byAccount.get(row.uuid());
            if (held != null && held.lastSeen() >= row.lastSeen()) {
                return;
            }
            remember(row);
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
