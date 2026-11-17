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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

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

    /**
     * How long a name learned from another backend stands against the store. The backend the player joined writes the
     * row on its async lane right after the join, so for this long the store may not show it yet; after that the
     * store has the word. The frames carry a roster stamp (session and sequence), which orders one backend's
     * announcements but says nothing next to the last-seen times other backends wrote, and the clocks of two machines
     * are never compared, so this backend's own monotonic clock times the window.
     */
    private static final long LEARNED_STANDS_MILLIS = 30_000L;
    /** How long a name confirmed against the store answers again without another read: a placeholder refreshes often. */
    private static final long CONFIRMED_FOR_MILLIS = 5_000L;
    /** The most names a confirmation is kept for; past it every one is let go at once. */
    private static final int CONFIRMED_NAMES = 1_024;
    /** How often an unreachable store is logged while it stays so. */
    private static final long WARN_EVERY_MILLIS = 60_000L;

    private static final long NEVER = Long.MIN_VALUE;

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
    /** Told each name a join here wrote to the store, once it is written. */
    private final List<BiConsumer<UUID, String>> stored = new CopyOnWriteArrayList<>();

    /** The accounts whose join here is still being written to the store, with how many writes are in flight. */
    private final ConcurrentHashMap<UUID, Integer> unwritten = new ConcurrentHashMap<>();
    /** When, on {@link #clock}, each account was last learned from another backend. */
    private final ConcurrentHashMap<UUID, Long> learnedAt = new ConcurrentHashMap<>();
    /** The holders the store confirmed under each lower-cased name, and when. */
    private final ConcurrentHashMap<String, Confirmed> confirmed = new ConcurrentHashMap<>();
    /** Counts the names recorded and learned, so a confirmation read across one is not kept. */
    private final AtomicLong heard = new AtomicLong();

    private final AtomicLong lastWarned = new AtomicLong(NEVER);

    private final Scheduler scheduler;
    private final Logger log;
    private final LongSupplier clock;

    public CachingPlayerNameIndex(Scheduler scheduler, Logger log) {
        this(scheduler, log, () -> System.nanoTime() / 1_000_000L);
    }

    /** @param clock a monotonic clock in milliseconds, for the windows this index keeps; never a wall clock */
    public CachingPlayerNameIndex(Scheduler scheduler, Logger log, LongSupplier clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The holders the store confirmed under one name, at {@code at} on the index's clock. */
    private record Confirmed(List<PlayerRef> holders, long at) {}

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
     * Tell {@code listener}, off the tick, each name a join here wrote to the store, once it is written, so what a name
     * decides in the store, such as who may wear it, is settled after the name is there. Returns what stops it.
     */
    public Runnable afterStored(BiConsumer<UUID, String> listener) {
        Objects.requireNonNull(listener, "listener");
        stored.add(listener);
        return () -> stored.remove(listener);
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
     * here and of those the network announces. A remembered holder stands against it only while its word is not in the
     * store yet: a join here whose row is still being written, or a name learned from another backend a moment ago.
     * Otherwise a holder the store lists under another name is let go from under this one, whatever the clocks of the
     * backends say. Those still being written come first, then the store's holders, most recently seen first. When the
     * memory held somebody under the name, the store's first holder is remembered, so the tick answers the same from
     * then on.
     *
     * <p>An answer is kept for a few seconds, so a name read again and again, as a placeholder does, reads the store
     * once. While the store cannot be read, the memory answers, and that is logged now and then.
     */
    @Override
    public List<PlayerRef> storedUnder(String name) {
        Objects.requireNonNull(name, "name");
        PlayerNameRepository store = repository.get();
        String key = key(name.strip());
        if (store == null || key.isEmpty()) {
            return byName(name);
        }
        long now = clock.getAsLong();
        Confirmed known = confirmed.get(key);
        if (known != null && now - known.at() < CONFIRMED_FOR_MILLIS) {
            return known.holders();
        }
        long heardBefore = heard.get();
        List<PlayerName> found;
        try {
            found = confirm(store, name, now);
        } catch (RuntimeException unreachable) {
            storeUnreachable(unreachable);
            return byName(name);
        }
        List<PlayerRef> holders = found.stream().map(PlayerName::ref).toList();
        if (heard.get() == heardBefore) {
            if (confirmed.size() >= CONFIRMED_NAMES) {
                confirmed.clear();
            }
            confirmed.put(key, new Confirmed(holders, now));
        }
        return holders;
    }

    /** The holders of {@code name}, the memory confirmed against the store as {@link #storedUnder} says. */
    private List<PlayerName> confirm(PlayerNameRepository store, String name, long now) {
        Map<UUID, PlayerName> fromStore = new LinkedHashMap<>();
        for (PlayerName row : store.underName(name, STORED_HOLDERS)) {
            fromStore.putIfAbsent(row.uuid(), row);
        }
        List<PlayerName> remembered = heldUnder(name);
        List<PlayerName> standing = new ArrayList<>();
        List<PlayerName> unstored = new ArrayList<>();
        for (PlayerName held : remembered) {
            if (stands(held.uuid(), now)) {
                fromStore.remove(held.uuid());
                standing.add(held);
            } else if (!fromStore.containsKey(held.uuid())) {
                // Not under this name in the store: renamed since on another backend, or never written at all.
                Optional<PlayerName> current = store.byUuid(held.uuid());
                if (current.isPresent()) {
                    rememberStored(current.get());
                } else {
                    unstored.add(held);
                }
            }
        }
        // Rows from one memory, so their times come from one clock.
        standing.sort(Comparator.comparingLong(PlayerName::lastSeen).reversed());
        List<PlayerName> found = new ArrayList<>(standing);
        found.addAll(fromStore.values());
        found.addAll(unstored);
        // Somebody else took a name the memory held: held from now on, so the tick names them. A name the memory holds
        // nobody under stays out of the window, which keeps the accounts seen here lately.
        if (!remembered.isEmpty()
                && !found.isEmpty()
                && !stands(found.getFirst().uuid(), now)) {
            rememberStored(found.getFirst());
        }
        return found;
    }

    /** Whether the memory's word on {@code account} is not in the store yet, so it stands against the store. */
    private boolean stands(UUID account, long now) {
        if (unwritten.containsKey(account)) {
            return true;
        }
        Long learned = learnedAt.get(account);
        if (learned == null) {
            return false;
        }
        if (now - learned < LEARNED_STANDS_MILLIS) {
            return true;
        }
        learnedAt.remove(account, learned);
        return false;
    }

    /** Log that the store could not be read, once a minute at most while it stays so. */
    private void storeUnreachable(RuntimeException failure) {
        long now = clock.getAsLong();
        long last = lastWarned.get();
        if (last != NEVER && now - last < WARN_EVERY_MILLIS) {
            return;
        }
        if (lastWarned.compareAndSet(last, now)) {
            log.warn(
                    "event=name_store_unreachable reason={}; names are answered from memory until it is back",
                    String.valueOf(failure.getMessage()));
        }
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
        Optional<PlayerName> stored;
        try {
            stored = store.byUuid(uuid);
        } catch (RuntimeException unreachable) {
            storeUnreachable(unreachable);
            return held;
        }
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
        nameHeard();
        PlayerNameRepository store = repository.get();
        if (store == null) {
            return;
        }
        // Until the row is written, this join stands against whatever the store still says of the account.
        unwritten.merge(uuid, 1, Integer::sum);
        scheduler.async(() -> {
            try {
                store.upsert(row);
                noteIfShared(store, uuid, trimmed);
            } catch (RuntimeException unreachable) {
                storeUnreachable(unreachable);
                return;
            } finally {
                unwritten.computeIfPresent(uuid, (account, writes) -> writes > 1 ? writes - 1 : null);
            }
            for (BiConsumer<UUID, String> listener : stored) {
                try {
                    listener.accept(uuid, trimmed);
                } catch (RuntimeException failure) {
                    log.warn(
                            "event=name_stored_listener_failed player={} reason={}",
                            uuid,
                            String.valueOf(failure.getMessage()));
                }
            }
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
        long now = clock.getAsLong();
        if (learnedAt.size() > CONFIRMED_NAMES) {
            learnedAt.values().removeIf(at -> now - at >= LEARNED_STANDS_MILLIS);
        }
        learnedAt.put(uuid, now);
        nameHeard();
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

    /** A name was recorded or learned: what the store confirmed before may be out of date. */
    private void nameHeard() {
        heard.incrementAndGet();
        confirmed.clear();
    }

    /**
     * Hold a row the store has the word on, unless the memory already holds the account under that name, or a join or
     * a learned name landed while the store was read: the newer word stands then.
     */
    private void rememberStored(PlayerName row) {
        synchronized (lock) {
            PlayerName held = byAccount.get(row.uuid());
            if (held != null && held.name().equals(row.name())) {
                return;
            }
            if (stands(row.uuid(), clock.getAsLong())) {
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
