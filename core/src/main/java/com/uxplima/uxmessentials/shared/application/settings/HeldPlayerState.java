package com.uxplima.uxmessentials.shared.application.settings;

import java.util.Map;
import java.util.Objects;
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
 * the database; a change lands in memory at once and is written back on the async lane. A player whose state was not
 * read, because the read failed, starts from {@code empty}.
 *
 * <p>Writes about one player land in the order the changes were made: only the newest state waits to be written, and
 * one writer per player writes it, so a slow write is followed by the latest state rather than overtaken by an older
 * one. After a write lands, {@code written} is told, so the change can be announced to the other backends.
 *
 * <p>A read again ({@link #refresh}, {@link #refreshAll}) keeps what is held while a change made here is still being
 * written, or was made while the read ran: the database is behind memory then, and the write in flight brings it level.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The held state, the state waiting to be written and the players being
 * written are {@link ConcurrentHashMap}s keyed by player, touched from the login thread, the player's own thread, the
 * bus and the async lane. Each change takes a number from one counter, so a read can tell whether a change landed
 * while it ran.
 *
 * @param <V> the state of one player, an immutable value
 */
public final class HeldPlayerState<V> implements HeldSettings {

    /** How many times a read that a change overtook is done again before it gives up on the player. */
    private static final int READ_ATTEMPTS = 3;

    /** Where the state lives between backends. Both calls do I/O and run off the tick. */
    public interface Storage<V> {

        /** The stored state of {@code player}, {@code empty} when they have none. */
        V read(UUID player);

        /** Store {@code state} as the whole state of {@code player}. */
        void write(UUID player, V state);
    }

    private final String kind;
    private final Storage<V> storage;
    private final V empty;
    private final Scheduler scheduler;
    private final Logger log;
    private final Consumer<UUID> written;
    private final ConcurrentHashMap<UUID, V> held = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, V> unwritten = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, V> unsettled = new ConcurrentHashMap<>();
    private final Set<UUID> writing = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> changedAt = new ConcurrentHashMap<>();
    private final AtomicLong changes = new AtomicLong();

    /**
     * @param kind what the state is, for the log
     * @param written told, on the async lane, that a player's state was written
     */
    public HeldPlayerState(
            String kind, Storage<V> storage, V empty, Scheduler scheduler, Logger log, Consumer<UUID> written) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.empty = Objects.requireNonNull(empty, "empty");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.written = Objects.requireNonNull(written, "written");
    }

    /** The state of {@code player}: what is held for them, or {@code empty}. Never reads the database. */
    public V get(UUID player) {
        return held.getOrDefault(Objects.requireNonNull(player, "player"), empty);
    }

    /** The state of every player held here. */
    public Map<UUID, V> all() {
        return Map.copyOf(held);
    }

    /** Change the state of {@code player} and write it back off the tick; the state it changed to. */
    public V update(UUID player, UnaryOperator<V> change) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(change, "change");
        V next = Objects.requireNonNull(
                held.compute(player, (id, current) -> change.apply(current == null ? empty : current)));
        changedAt.put(player, changes.incrementAndGet());
        unsettled.put(player, next);
        unwritten.put(player, next);
        flush(player);
        return next;
    }

    @Override
    public void load(UUID player) {
        Objects.requireNonNull(player, "player");
        for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            long before = changedAt.getOrDefault(player, 0L);
            V read;
            try {
                read = storage.read(player);
            } catch (RuntimeException unreachable) {
                log.warn(
                        "event=player_state_read_failed kind={} player={} reason={}; the defaults hold until the next"
                                + " login",
                        kind,
                        player,
                        String.valueOf(unreachable.getMessage()));
                return;
            }
            boolean[] settled = {true};
            held.compute(player, (id, current) -> {
                if (!behind(player, before)) {
                    return read;
                }
                // A change made here is newer than the row: keep it, or bring back the one still being written.
                V newer = current != null ? current : unsettled.get(player);
                settled[0] = newer != null;
                return newer;
            });
            if (settled[0]) {
                return;
            }
        }
    }

    @Override
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        held.remove(player);
        if (!pending(player)) {
            changedAt.remove(player);
        }
    }

    @Override
    public void refresh(UUID player) {
        Objects.requireNonNull(player, "player");
        if (held.containsKey(player)) {
            scheduler.async(() -> reload(player));
        }
    }

    @Override
    public void refreshAll() {
        for (UUID player : held.keySet()) {
            reload(player);
        }
    }

    private void reload(UUID player) {
        long before = changedAt.getOrDefault(player, 0L);
        V read;
        try {
            read = storage.read(player);
        } catch (RuntimeException unreachable) {
            log.warn(
                    "event=player_state_reload_failed kind={} player={} reason={}",
                    kind,
                    player,
                    String.valueOf(unreachable.getMessage()));
            return;
        }
        // Only a player still held is updated: one who left meanwhile is read again on their next login.
        held.computeIfPresent(player, (id, current) -> behind(player, before) ? current : read);
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
        }
    }

    private void write(UUID player, V state) {
        try {
            storage.write(player, state);
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
        unsettled.remove(player, state);
        written.accept(player);
    }
}
