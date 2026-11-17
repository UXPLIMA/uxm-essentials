package com.uxplima.uxmessentials.shared.application.settings;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * One kind of per-player state every backend of a network shares through the database, held in memory for the
 * players online here. The state is read when a player logs in, off the tick, so a check on the tick never waits on
 * the database; a change lands in memory at once and is written back on the async lane.
 *
 * <p>A change that builds on the current state ({@link #update}) is refused for a player whose state was not read,
 * because memory would not know what it builds on; a change that replaces the whole state ({@link #set}) is always
 * taken. What is written is the difference from what the database was last known to hold, so a write never removes
 * what this backend did not know about.
 *
 * <p>Writes about one player land in the order the changes were made: only the newest state waits to be written, and
 * one writer per player writes it, so a slow write is followed by the latest state rather than overtaken by an older
 * one. After a write lands, {@code written} is told, so the change can be announced to the other backends.
 *
 * <p>A read keeps what is held while a change made here is still being written, or was made while the read ran: the
 * database is behind memory then, and the write in flight brings it level. A change announced from another backend
 * while the login read runs makes that read run again, since it may have read the row before the change landed.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Every map is a {@link ConcurrentHashMap} keyed by player, touched from the
 * login thread, the player's own thread, the bus and the async lane. Each change takes a number from one counter, so
 * a read can tell whether a change landed while it ran.
 *
 * @param <V> the state of one player, an immutable value
 */
public final class HeldPlayerState<V> implements HeldSettings {

    /** Where the state lives between backends. Both calls do I/O and run off the tick. */
    public interface Storage<V> {

        /** The stored state of {@code player}, {@code empty} when they have none. */
        V read(UUID player);

        /** Store the change from {@code before}, what the database was last known to hold, to {@code after}. */
        void write(UUID player, V before, V after);
    }

    /** How many times a read that a change overtook is done again before it settles. */
    private static final int READ_ATTEMPTS = 3;

    private final String kind;
    private final Storage<V> storage;
    private final V empty;
    private final Scheduler scheduler;
    private final Logger log;
    private final Consumer<UUID> written;
    private final Consumer<UUID> read;
    private final ConcurrentHashMap<UUID, V> held = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, V> stored = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, V> unwritten = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, V> unsettled = new ConcurrentHashMap<>();
    private final Set<UUID> writing = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> changedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> announcedAt = new ConcurrentHashMap<>();
    private final AtomicLong changes = new AtomicLong();

    /**
     * @param kind what the state is, for the log
     * @param written told, on the async lane, that a player's state was written
     */
    public HeldPlayerState(
            String kind, Storage<V> storage, V empty, Scheduler scheduler, Logger log, Consumer<UUID> written) {
        this(kind, storage, empty, scheduler, log, written, player -> {});
    }

    /**
     * A held state that also tells {@code read} when a read from the database became what is held for a player, on
     * the thread that read it, so a caller can show a change another backend made.
     *
     * @param kind what the state is, for the log
     * @param written told, on the async lane, that a player's state was written
     * @param read told after a player's stored state was read into memory
     */
    public HeldPlayerState(
            String kind,
            Storage<V> storage,
            V empty,
            Scheduler scheduler,
            Logger log,
            Consumer<UUID> written,
            Consumer<UUID> read) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.empty = Objects.requireNonNull(empty, "empty");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.written = Objects.requireNonNull(written, "written");
        this.read = Objects.requireNonNull(read, "read");
    }

    /** The state of {@code player}: what is held for them, or {@code empty}. Never reads the database. */
    public V get(UUID player) {
        return held.getOrDefault(Objects.requireNonNull(player, "player"), empty);
    }

    /** What the database was last known to hold for {@code player}, if this backend read or wrote it. */
    public Optional<V> stored(UUID player) {
        return Optional.ofNullable(stored.get(Objects.requireNonNull(player, "player")));
    }

    /** The state of every player held here. */
    public Map<UUID, V> all() {
        return Map.copyOf(held);
    }

    @Override
    public boolean holds(UUID player) {
        return held.containsKey(Objects.requireNonNull(player, "player"));
    }

    /**
     * Change the state of {@code player} from what is held and write it back off the tick; the state it changed to, or
     * empty when their state was not read, so there is nothing to build on.
     */
    public Optional<V> update(UUID player, UnaryOperator<V> change) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(change, "change");
        V next = held.computeIfPresent(player, (id, current) -> change.apply(current));
        if (next == null) {
            return Optional.empty();
        }
        changed(player, next);
        return Optional.of(next);
    }

    /** Replace the whole state of {@code player} and write it back off the tick. Taken whether or not it was read. */
    public void set(UUID player, V state) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        held.put(player, state);
        changed(player, state);
    }

    private void changed(UUID player, V next) {
        changedAt.put(player, changes.incrementAndGet());
        unsettled.put(player, next);
        unwritten.put(player, next);
        flush(player);
    }

    @Override
    public void load(UUID player) {
        Objects.requireNonNull(player, "player");
        announcedAt.putIfAbsent(player, 0L);
        try {
            for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
                long announced = announcedAt.getOrDefault(player, 0L);
                if (readOnce(player) && announcedAt.getOrDefault(player, 0L) == announced) {
                    return;
                }
            }
        } finally {
            announcedAt.remove(player);
        }
    }

    /** One read of the row into memory; whether it settled the player. */
    private boolean readOnce(UUID player) {
        long before = changedAt.getOrDefault(player, 0L);
        V row;
        try {
            row = storage.read(player);
        } catch (RuntimeException unreachable) {
            log.warn(
                    "event=player_state_read_failed kind={} player={} reason={}; changes wait for the next login",
                    kind,
                    player,
                    String.valueOf(unreachable.getMessage()));
            return true;
        }
        boolean[] settled = {true};
        boolean[] taken = {false};
        held.compute(player, (id, current) -> {
            if (!behind(player, before)) {
                stored.put(player, row);
                taken[0] = true;
                return row;
            }
            // A change made here is newer than the row: keep it, or bring back the one still being written.
            V newer = current != null ? current : unsettled.get(player);
            settled[0] = newer != null;
            return newer;
        });
        if (taken[0]) {
            read.accept(player);
        }
        return settled[0];
    }

    @Override
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        held.remove(player);
        if (!pending(player)) {
            stored.remove(player);
            changedAt.remove(player);
        }
    }

    @Override
    public void refresh(UUID player) {
        Objects.requireNonNull(player, "player");
        // A login read in flight may have read the row before this change landed: it reads again.
        announcedAt.computeIfPresent(player, (id, count) -> count + 1);
        if (held.containsKey(player)) {
            scheduler.async(() -> {
                if (held.containsKey(player)) {
                    readOnce(player);
                }
            });
        }
    }

    /** Whether memory is ahead of a read that started when the last change was {@code before}. */
    private boolean behind(UUID player, long before) {
        return pending(player) || changedAt.getOrDefault(player, 0L) != before;
    }

    private boolean pending(UUID player) {
        return unsettled.containsKey(player) || unwritten.containsKey(player) || writing.contains(player);
    }

    private void flush(UUID player) {
        if (writing.add(player)) {
            scheduler.async(() -> drain(player));
        }
    }

    private void drain(UUID player) {
        try {
            V next;
            while ((next = unwritten.remove(player)) != null) {
                write(player, next);
            }
        } finally {
            writing.remove(player);
        }
        // A change that arrived after the last look and before the writer let go would otherwise wait for the next one.
        if (unwritten.containsKey(player)) {
            flush(player);
        } else if (!held.containsKey(player) && !pending(player)) {
            // The player left while their change was written: nothing here needs what was known about them.
            stored.remove(player);
            changedAt.remove(player);
        }
    }

    private void write(UUID player, V state) {
        try {
            storage.write(player, stored.getOrDefault(player, empty), state);
        } catch (RuntimeException failure) {
            unsettled.remove(player, state);
            log.warn(
                    "event=player_state_write_failed kind={} player={} reason={}; the change holds on this server"
                            + " until the player leaves",
                    kind,
                    player,
                    String.valueOf(failure.getMessage()));
            return;
        }
        stored.put(player, state);
        unsettled.remove(player, state);
        written.accept(player);
    }
}
