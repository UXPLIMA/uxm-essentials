package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemHandBack;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;
import org.jspecify.annotations.Nullable;

/**
 * The vault windows currently open, and the writes of the ones that just closed. Every vault's items are in exactly
 * one place at a time: one open window, or one write on its way to the database, or the database.
 *
 * <p>A vault lives in at most one window. Two windows seeded from the same row and written back on close would let
 * the later close restore what the earlier one had taken out, an item dupe, so a second window is refused while one
 * holds the vault, whoever asks. The viewer already holding it opens it again by closing their window first: its
 * close writes it like any other, and the new window is filled from that write's pending state, so the items are in
 * one place throughout and nothing put into the old window is left behind in it.
 *
 * <p>A closing window's items stay here as the vault's pending state until their write has landed. The write runs off
 * the tick, and the vault a reopen is filled from was read through a cache that may predate it, so a reopen filled from
 * that read would show items the player had just taken out. A reopen is filled from the pending state instead. Once
 * the write has landed, a copy read before it is recognised by its {@link VaultStamp}: the row's version goes up by one
 * per item write inside the database, so a copy of the same row with a lower version than the one the write produced is
 * older than the write, whichever server wrote what and whatever their clocks say. Such a copy is filled from what was
 * written. When the write finds the vault deleted, the items go back to the owner through {@link VaultItemHandBack},
 * unless a reopen has already taken them into a new window; then that window's close takes care of them, and a copy of
 * the deleted row opens empty.
 *
 * <p>Writes of one vault never overlap and a write that a later close has superseded is skipped, so an older snapshot
 * can never land after a newer one. They are serialised by a fixed set of locks picked by the vault's id, held across
 * the write: a write only waits for another write of a vault that shares its lock, and a per-vault queue would need its
 * own executor and its own shutdown, for no difference a player could notice.
 *
 * <p>Every write is a compare-and-set. A snapshot's base is the stamp of the row its items were built on: the copy its
 * window was filled from, or this server's own last write of the vault when that is newer. Every attempt to write one
 * snapshot records the same random writer token in the row, and writes, in one statement, only while the row is still
 * at the base or already carries that token. When nothing was written, or the write threw, the row is read to learn
 * why: the row carrying the token means the write landed; no row, or a row of another lineage, means the owner's vault
 * is gone and the items go back to the owner (a staff window inserts the row again instead, since staff fill vaults
 * whether or not they exist); the row still at the base means nothing was written, and the write is tried again.
 *
 * <p>A row that moved past the base under another writer means another server wrote the vault while this server's
 * window was open, or between a failed write and its retry: a staff member on one server and the owner on another
 * can each hold the vault, since a window only refuses a second viewer on its own server, and a reloaded module may
 * write a vault while the old instance's retry is still queued. Writing over that row would undo the other server's
 * changes and settling would drop this one's, so neither happens: the snapshot stays pending for a reopen on this
 * server, and its items go to the log for an operator with a line saying two servers edited the vault. Whichever of
 * the two writes reaches the store second is the one that is logged; the store keeps the first.
 *
 * <p>A write that throws is never answered by handing the items back: the row usually still holds what the window was
 * opened with, so handing the window's items back as well would duplicate everything the two have in common, and a
 * write that was applied but not acknowledged would be given twice. The snapshot stays pending instead (a reopen still
 * takes it over) and the write is retried with a growing delay. When the retries run out the snapshot stays pending in
 * memory and its items go to the log the same way.
 *
 * <p>The window handle {@code W} is the adapter's GUI; this class never looks inside it. The caller hands over a
 * window's items when it releases it.
 *
 * @param <W> the adapter's window type
 */
public final class VaultWindows<W> {

    /** How long a landed write is remembered for reopens whose read was already under way when it landed. */
    private static final Duration REMEMBER_LANDED = Duration.ofMinutes(1);

    private static final int WRITE_LOCKS = 64;

    /** How many times a write that threw is tried again before its items are left to an operator. */
    private static final int RETRIES = 5;

    /** The delay before the first retry; each further retry waits twice as long. */
    private static final Duration FIRST_RETRY = Duration.ofMillis(500);

    private final SaveVault saveVault;
    private final VaultRepository repository;
    private final VaultItemHandBack handBack;
    private final Scheduler scheduler;
    private final BooleanSupplier running;
    private final Clock clock;
    private final Logger log;
    private final Map<VaultId, Window<W>> open = new HashMap<>();
    private final Map<VaultId, Closed<W>> closed = new HashMap<>();
    /** The stamp of each vault's last write that landed from this server, the base a failed write is judged against. */
    private final Map<VaultId, VaultStamp> landedHere = new HashMap<>();

    private final Object[] writeLocks = new Object[WRITE_LOCKS];
    private int writesUnderWay;

    public VaultWindows(
            SaveVault saveVault,
            VaultRepository repository,
            VaultItemHandBack handBack,
            Scheduler scheduler,
            BooleanSupplier running,
            Clock clock,
            Logger log) {
        this.saveVault = Objects.requireNonNull(saveVault, "saveVault");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.handBack = Objects.requireNonNull(handBack, "handBack");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.running = Objects.requireNonNull(running, "running");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
        for (int lock = 0; lock < WRITE_LOCKS; lock++) {
            writeLocks[lock] = new Object();
        }
    }

    /**
     * Claim {@code vault} for {@code window}, shown to {@code viewer}. Returns the items to fill the new window with:
     * the pending or just written items of a window that closed, or else the vault's stored items. Empty when a window
     * already holds the vault, the viewer's own included, and then nothing is claimed: the caller closes that window
     * first.
     */
    public synchronized Optional<VaultContents> claim(PlayerRef viewer, PlayerRef owner, Vault vault, W window) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(window, "window");
        VaultId id = vault.id();
        if (open.containsKey(id)) {
            return Optional.empty();
        }
        open.put(id, new Window<>(viewer, owner, vault, window));
        return Optional.of(seedFrom(vault));
    }

    /** The window holding {@code id}, if one does. */
    public synchronized Optional<Window<W>> holder(VaultId id) {
        return Optional.ofNullable(open.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Release {@code window}'s claim on {@code id}, keeping {@code contents} (the items left in it) as the vault's
     * pending state. Returns the write to run, on the async lane or on the calling thread; empty when the window was
     * already released, and then nothing is to be written.
     */
    public Optional<Runnable> release(VaultId id, W window, VaultContents contents) {
        return snapshot(id, window, contents, true);
    }

    /**
     * Write {@code contents} of a window that stays open, keeping it claimed. Returns the write to run; empty when the
     * window does not hold the vault. A vault found deleted is not handed back: the items are still in the window, and
     * its close takes care of them.
     */
    public Optional<Runnable> checkpoint(VaultId id, W window, VaultContents contents) {
        return snapshot(id, window, contents, false);
    }

    /**
     * Release every vault {@code viewer} holds in a window they are not looking at, because they left. Returns the
     * writes to run, as a close would; {@code released} is told of each window released, so it can be emptied of what
     * was just written.
     *
     * <p>Such a window is one whose open never came up and was not released when it was refused, and its claim would otherwise block every
     * other viewer of that vault until this viewer came back. Releasing it is safe because nothing can reach its
     * items any more: its viewer is gone, nobody else ever sees another viewer's window, and the caller has already
     * decided its open as refused, so a late open is cancelled rather than shown. Its items are written like a
     * close's, so the next viewer starts from them. A window that {@code seen} reports as still looked at is live
     * and keeps its claim: releasing it would let a second window be filled from the same row while this one can
     * still be emptied, and the two writes would duplicate items. Its own close writes it.
     */
    public List<Runnable> releaseLeft(
            UUID viewer, Predicate<W> seen, Function<W, VaultContents> contents, Consumer<W> released) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(seen, "seen");
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(released, "released");
        List<Runnable> writes = new ArrayList<>();
        for (Window<W> held : held()) {
            W window = held.window();
            if (held.viewer().uuid().equals(viewer) && !seen.test(window)) {
                Optional<Runnable> write = release(held.vault().id(), window, contents.apply(window));
                if (write.isPresent()) {
                    released.accept(window);
                    writes.add(write.get());
                }
            }
        }
        return writes;
    }

    /** True when {@code window} holds {@code id}, so its close is the one that writes the vault back. */
    public synchronized boolean holds(VaultId id, W window) {
        Window<W> current = open.get(Objects.requireNonNull(id, "id"));
        return current != null && current.window() == window;
    }

    /** Every open window, for the module stop to close. */
    public synchronized List<Window<W>> held() {
        return List.copyOf(open.values());
    }

    /** How many windows are open. */
    public synchronized int count() {
        return open.size();
    }

    /**
     * Forget a landed or failed write of {@code id}, because another server reported a change to that vault. The stamp
     * comparison already tells a stale copy from a fresh one; this only drops memory that is no longer needed. A write
     * still on its way is kept, since its items exist nowhere else.
     */
    public synchronized void forget(VaultId id) {
        Closed<W> last = closed.get(Objects.requireNonNull(id, "id"));
        if (last != null && (last.state == State.LANDED || last.state == State.GONE)) {
            closed.remove(id);
            landedHere.remove(id);
        }
    }

    /**
     * The vaults whose items are not yet safe in the store: open windows and snapshots whose write has not landed, as
     * {@code owner#index}. For the stop to name what is at risk when it cannot wait.
     */
    public synchronized List<String> unsettled() {
        List<String> names = new ArrayList<>();
        open.keySet().forEach(id -> names.add(id.owner() + "#" + id.index() + " (open)"));
        closed.forEach((id, last) -> {
            if (last.state == State.PENDING) {
                names.add(id.owner() + "#" + id.index() + " (not written)");
            }
        });
        return names;
    }

    /**
     * Write the items of every snapshot that never reached the store to the log, for an operator to give back by hand.
     * Called when the module stops, after which nothing will retry them.
     */
    public void logUnwritten() {
        List<Closed<W>> unwritten = new ArrayList<>();
        synchronized (this) {
            closed.values().stream().filter(last -> last.state == State.PENDING).forEach(unwritten::add);
        }
        unwritten.forEach(last -> logForOperator(last, "the module stopped before they were written"));
    }

    /**
     * Wait until every window is closed and every write has finished, at most {@code timeout}. Returns whether that
     * happened in time. The module stop calls this so the next instance of the module does not read a vault whose
     * write this instance still has on its way.
     */
    public synchronized boolean awaitIdle(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!open.isEmpty() || writesUnderWay > 0) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            try {
                wait(Math.max(1L, left / 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private Optional<Runnable> snapshot(VaultId id, W window, VaultContents contents, boolean closing) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(contents, "contents");
        Closed<W> written;
        synchronized (this) {
            Window<W> current = open.get(id);
            if (current == null || current.window() != window) {
                return Optional.empty();
            }
            if (closing) {
                open.remove(id);
                notifyAll();
            }
            // A window that stays open still holds these items, so a failed write must not hand them back.
            written = new Closed<>(current, contents, clock.instant(), closing ? State.PENDING : State.ADOPTED);
            closed.put(id, written);
            writesUnderWay++;
            forgetSettled();
        }
        return Optional.of(() -> write(id, written));
    }

    /** Fill a new window on {@code vault}, which nobody holds, given what closed windows left. Holds the lock. */
    private VaultContents seedFrom(Vault vault) {
        Closed<W> last = closed.get(vault.id());
        if (last == null) {
            return vault.contents();
        }
        switch (last.state) {
            case PENDING, ADOPTED -> {
                // Not written yet: these items are the vault's latest state, and the new window now holds them.
                last.state = State.ADOPTED;
                return last.contents;
            }
            case LANDED -> {
                VaultStamp landed = Objects.requireNonNull(last.landed, "landed");
                if (vault.stamp().before(landed)) {
                    return last.contents;
                }
                // The read already holds the write, so neither the snapshot nor its stamp is needed any more.
                closed.remove(vault.id());
                landedHere.remove(vault.id());
                return vault.contents();
            }
            case GONE -> {
                if (vault.stamp().sameRow(last.window.vault().stamp())) {
                    return VaultContents.empty(); // a copy of the deleted row; its items went back to the owner
                }
                closed.remove(vault.id());
                landedHere.remove(vault.id());
                return vault.contents();
            }
        }
        throw new IllegalStateException("unknown state " + last.state);
    }

    private void write(VaultId id, Closed<W> written) {
        boolean finished = true;
        try {
            synchronized (writeLocks[Math.floorMod(id.hashCode(), WRITE_LOCKS)]) {
                VaultStamp base;
                synchronized (this) {
                    if (closed.get(id) != written) {
                        return; // a later snapshot of this vault holds these items and writes them
                    }
                    VaultStamp fixed = written.base;
                    if (fixed == null) {
                        fixed = latest(written.window.vault().stamp(), landedHere.get(id));
                        written.base = fixed;
                    }
                    base = fixed;
                }
                Outcome outcome;
                RuntimeException failure = null;
                try {
                    outcome = attempt(id, written, base);
                } catch (RuntimeException thrown) {
                    failure = thrown;
                    outcome = learn(id, written, base);
                }
                switch (outcome.kind()) {
                    case RETRY -> finished = !retryLater(id, written, failure);
                    case CONFLICT -> conflict(id, written);
                    case LANDED, GONE -> {
                        if (failure != null) {
                            log.warn(
                                    "event=vault_write_failed owner={} index={} (the store shows {})",
                                    id.owner(),
                                    id.index(),
                                    outcome.kind() == Kind.LANDED ? "it landed" : "the vault is gone");
                        }
                        settle(id, written, Optional.ofNullable(outcome.landed()));
                    }
                }
            }
        } finally {
            if (finished) {
                synchronized (this) {
                    writesUnderWay--;
                    notifyAll();
                }
            }
        }
    }

    /** Record where a written snapshot's items are now, and hand them back when its vault was found deleted. */
    private void settle(VaultId id, Closed<W> written, Optional<VaultStamp> landed) {
        boolean giveBack = false;
        synchronized (this) {
            written.settledAt = clock.instant();
            if (landed.isPresent()) {
                written.landed = landed.get();
                landedHere.put(id, landed.get());
                if (written.state == State.PENDING) {
                    written.state = State.LANDED;
                }
            } else if (written.state == State.PENDING) {
                written.state = State.GONE;
                giveBack = true;
            }
        }
        if (giveBack && !written.contents.isEmpty()) {
            handBack.handBack(written.window.owner(), written.contents);
        }
    }

    /**
     * Write a snapshot once: only over the row at the snapshot's base or over its own earlier attempt, reading the row
     * to learn why when that wrote nothing. A staff window that found the row missing inserts it again.
     */
    private Outcome attempt(VaultId id, Closed<W> written, VaultStamp base) {
        Window<W> window = written.window;
        if (!window.staffView() || !written.rowMissing) {
            Optional<VaultStamp> stored =
                    saveVault.save(window.owner(), window.vault(), written.contents, written.at, written.writer, base);
            if (stored.isPresent()) {
                return Outcome.landed(stored.get());
            }
            Outcome learned = learn(id, written, base);
            if (!window.staffView() || !written.rowMissing || learned.kind() != Kind.RETRY) {
                return learned;
            }
        }
        // Staff fill a vault whether or not it has a row, and no row means no other write to keep.
        return Outcome.landed(
                saveVault.keep(window.owner(), window.vault(), written.contents, written.at, written.writer));
    }

    /**
     * Read the row to learn what a write that threw, or one that wrote nothing, did. The row carrying this
     * snapshot's token means it landed. A missing row, or a row of another lineage, means the owner's vault was
     * deleted; a staff window writes its items back anyway, so a missing row is retried there and a row someone else
     * created is a conflict. A row still at the base means nothing was written: retry. A row that moved on under
     * another writer means another server wrote in between. A row that cannot be read is retried.
     */
    private Outcome learn(VaultId id, Closed<W> written, VaultStamp base) {
        Optional<VaultRepository.LastWrite> row;
        try {
            row = repository.lastWrite(id);
        } catch (RuntimeException unreadable) {
            return Outcome.RETRY;
        }
        boolean staff = written.window.staffView();
        written.rowMissing = row.isEmpty();
        if (row.isEmpty()) {
            return staff ? Outcome.RETRY : Outcome.GONE;
        }
        VaultStamp now = row.get().stamp();
        if (written.writer.equals(row.get().writer())) {
            return Outcome.landed(now);
        }
        if (!now.sameRow(base)) {
            return staff ? Outcome.CONFLICT : Outcome.GONE;
        }
        return now.version() == base.version() ? Outcome.RETRY : Outcome.CONFLICT;
    }

    /**
     * Another server wrote the vault after the row this snapshot was built on. Its items stay pending in memory, where
     * a reopen on this server takes them over, and go to the log so an operator can reconcile the two.
     */
    private void conflict(VaultId id, Closed<W> written) {
        log.warn(
                "event=vault_write_conflict owner={} index={} (two servers edited this vault: another server wrote it"
                        + " after the copy this server's window was built on, so this server's items were not written"
                        + " over it)",
                id.owner(),
                id.index());
        logForOperator(written, "two servers edited the vault; compare them with what it holds now");
    }

    /**
     * Try a failed write again after a growing delay. Returns false when no retry is left, or none can run because the
     * plugin has stopped; the snapshot then stays pending in memory, so a reopen still takes it over, and its items go
     * to the log.
     */
    private boolean retryLater(VaultId id, Closed<W> written, @Nullable RuntimeException failure) {
        if (running.getAsBoolean() && written.retries < RETRIES) {
            Duration delay = FIRST_RETRY.multipliedBy(1L << written.retries);
            written.retries++;
            log.warn(
                    "event=vault_write_retry owner={} index={} attempt={} in={} cause={}",
                    id.owner(),
                    id.index(),
                    written.retries,
                    delay,
                    failure == null
                            ? "the retry wrote nothing and the store could not tell why"
                            : String.valueOf(failure.getMessage()));
            scheduler.asyncAfter(delay, () -> write(id, written));
            return true;
        }
        String gaveUp = "event=vault_write_gave_up owner=" + id.owner() + " index=" + id.index() + " after "
                + written.retries + " retries";
        if (failure == null) {
            log.warn(gaveUp);
        } else {
            log.error(gaveUp, failure);
        }
        logForOperator(written, "every write of them failed");
        return false;
    }

    private void logForOperator(Closed<W> written, String why) {
        VaultId id = written.window.vault().id();
        log.warn(
                "event=vault_items_unwritten owner={} index={} items={} ({}; they stay in memory and a reopen"
                        + " takes them over, but they are lost if the server stops: give them back by hand)",
                id.owner(),
                id.index(),
                written.contents
                        .payload()
                        .map(Base64.getEncoder()::encodeToString)
                        .orElse(""),
                why);
    }

    /** The later of two stamps of the same row; the first when they name different rows. */
    private static VaultStamp latest(VaultStamp read, @Nullable VaultStamp landed) {
        if (landed != null && read.before(landed)) {
            return landed;
        }
        return read;
    }

    /**
     * Drop writes that landed long enough ago that no read started before them can still be under way. Holds the
     * lock.
     */
    private void forgetSettled() {
        Instant cutoff = clock.instant().minus(REMEMBER_LANDED);
        List<VaultId> settled = new ArrayList<>();
        closed.forEach((id, last) -> {
            Instant at = last.settledAt;
            if ((last.state == State.LANDED || last.state == State.GONE) && at != null && at.isBefore(cutoff)) {
                settled.add(id);
            }
        });
        settled.forEach(closed::remove);
        // A stamp is only the base of the next write of a vault this server still remembers a snapshot of.
        landedHere.keySet().retainAll(closed.keySet());
    }

    /** Where a snapshot's items are. */
    private enum State {
        /** Only in the write on its way. */
        PENDING,
        /** In a window that is open again; the write only mirrors them. */
        ADOPTED,
        /** In the database. */
        LANDED,
        /** Handed back to the owner, because the vault was deleted. */
        GONE
    }

    /** What became of one attempt to write a snapshot, and what to do next. */
    private enum Kind {
        /** The row holds the snapshot. */
        LANDED,
        /** The vault was deleted. */
        GONE,
        /** Nothing was written, or what happened cannot be told yet: try again. */
        RETRY,
        /** Another server wrote the vault in between: leave its write alone. */
        CONFLICT
    }

    /**
     * The outcome of one attempt to write a snapshot.
     *
     * @param kind what became of it
     * @param landed the row's stamp after the write, for {@link Kind#LANDED} only
     */
    private record Outcome(Kind kind, @Nullable VaultStamp landed) {

        static final Outcome GONE = new Outcome(Kind.GONE, null);
        static final Outcome RETRY = new Outcome(Kind.RETRY, null);
        static final Outcome CONFLICT = new Outcome(Kind.CONFLICT, null);

        static Outcome landed(VaultStamp stamp) {
            return new Outcome(Kind.LANDED, stamp);
        }
    }

    /**
     * The items of one snapshot of a vault, and where they are. Guarded by the {@link VaultWindows} lock, except the
     * fields only its write touches, which the vault's write lock guards.
     */
    private static final class Closed<W> {
        private final Window<W> window;
        private final VaultContents contents;
        private final Instant at;
        /** Recorded in the row by every attempt to write this snapshot, so the row tells whether one of them landed. */
        private final String writer = UUID.randomUUID().toString();

        private State state;
        private @Nullable VaultStamp landed;
        private @Nullable Instant settledAt;
        /** The row's stamp before the first attempt: a retry writes only while the row is still there. */
        private @Nullable VaultStamp base;
        /** The last read found no row, so a staff retry inserts one again. */
        private boolean rowMissing;

        private int retries;

        Closed(Window<W> window, VaultContents contents, Instant at, State state) {
            this.window = window;
            this.contents = contents;
            this.at = at;
            this.state = state;
        }
    }

    /**
     * One open window.
     *
     * @param viewer who the window is shown to
     * @param owner whose vault it is
     * @param vault the vault as it was when the window opened
     * @param window the adapter's GUI
     * @param <W> the adapter's window type
     */
    public record Window<W>(PlayerRef viewer, PlayerRef owner, Vault vault, W window) {

        public Window {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(vault, "vault");
            Objects.requireNonNull(window, "window");
        }

        /** True when staff opened another player's vault. */
        public boolean staffView() {
            return !viewer.uuid().equals(owner.uuid());
        }
    }
}
