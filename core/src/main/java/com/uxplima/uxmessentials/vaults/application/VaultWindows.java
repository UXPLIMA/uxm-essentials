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
import java.util.function.BooleanSupplier;
import java.util.function.Function;

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
 * the later close restore what the earlier one had taken out, an item dupe, so a second viewer is refused. The viewer
 * already holding a vault may open it again: the new window takes the old window's live items over and the old window
 * is never written back on its own.
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
 * <p>A write that throws is never answered by handing the items back: the row usually still holds what the window was
 * opened with, so handing the window's items back as well would duplicate everything the two have in common, and a
 * write that was applied but not acknowledged would be given twice. The snapshot stays pending instead (a reopen still
 * takes it over), the row is read to learn what happened, and the write is retried with a growing delay: no row means
 * the vault is gone and the items go back to the owner; a row of the same lineage at a higher version than this server
 * last knew means the write landed. When the retries run out the snapshot stays pending in memory and its items are
 * written to the log for an operator.
 *
 * <p>The window handle {@code W} is the adapter's GUI; this class never looks inside it, it only asks the caller for
 * its live items when it is taken over.
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
     * the live items of the viewer's own window it takes over ({@code takeOver} reads and empties that window), the
     * pending or just written items of a window that closed, or else the vault's stored items. Empty when another
     * viewer holds the vault, and then nothing is claimed.
     */
    public Optional<VaultContents> claim(
            PlayerRef viewer, PlayerRef owner, Vault vault, W window, Function<W, VaultContents> takeOver) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(takeOver, "takeOver");
        Window<W> retired;
        VaultContents seed;
        synchronized (this) {
            VaultId id = vault.id();
            Window<W> current = open.get(id);
            if (current != null && !current.viewer().uuid().equals(viewer.uuid())) {
                return Optional.empty();
            }
            open.put(id, new Window<>(viewer, owner, vault, window));
            retired = current;
            seed = retired == null ? seedFrom(vault) : VaultContents.empty();
        }
        // The old window is the viewer's own, so it is read on the thread this runs on, outside the lock.
        return Optional.of(retired == null ? seed : takeOver.apply(retired.window()));
    }

    /**
     * Release {@code window}'s claim on {@code id}, keeping {@code contents} (the items left in it) as the vault's
     * pending state. Returns the write to run, on the async lane or on the calling thread; empty when the window was
     * taken over or already released, and then nothing is to be written.
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
                closed.remove(vault.id());
                return vault.contents();
            }
            case GONE -> {
                if (vault.stamp().sameRow(last.window.vault().stamp())) {
                    return VaultContents.empty(); // a copy of the deleted row; its items went back to the owner
                }
                closed.remove(vault.id());
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
                    base = latest(written.window.vault().stamp(), landedHere.get(id));
                }
                Optional<VaultStamp> landed;
                try {
                    landed = store(written);
                } catch (RuntimeException failure) {
                    Optional<Optional<VaultStamp>> learned = learn(id, written, base);
                    if (learned.isEmpty()) {
                        finished = !retryLater(id, written, failure);
                        return;
                    }
                    log.warn(
                            "event=vault_write_failed owner={} index={} (the store shows {})",
                            id.owner(),
                            id.index(),
                            learned.get().isPresent() ? "it landed" : "the vault is gone");
                    landed = learned.get();
                }
                settle(id, written, landed);
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
     * After a write threw, read the row to learn what happened: empty when that cannot be told (try again), else the
     * outcome to settle with, the row's stamp when the write landed or nothing when the vault is gone. Only the owner's
     * own window can find its vault gone; a staff upsert would have recreated it, so a missing row means it did not
     * apply.
     */
    private Optional<Optional<VaultStamp>> learn(VaultId id, Closed<W> written, VaultStamp base) {
        Optional<Vault> row;
        try {
            row = repository.find(id);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
        if (row.isEmpty()) {
            return written.window.staffView() ? Optional.empty() : Optional.of(Optional.empty());
        }
        VaultStamp now = row.get().stamp();
        if (now.sameRow(base) && now.version() > base.version()) {
            return Optional.of(Optional.of(now));
        }
        return Optional.empty();
    }

    /**
     * Try a failed write again after a growing delay. Returns false when no retry is left, or none can run because the
     * plugin has stopped; the snapshot then stays pending in memory, so a reopen still takes it over, and its items go
     * to the log.
     */
    private boolean retryLater(VaultId id, Closed<W> written, RuntimeException failure) {
        if (running.getAsBoolean() && written.retries < RETRIES) {
            Duration delay = FIRST_RETRY.multipliedBy(1L << written.retries);
            written.retries++;
            log.warn(
                    "event=vault_write_retry owner={} index={} attempt={} in={} cause={}",
                    id.owner(),
                    id.index(),
                    written.retries,
                    delay,
                    String.valueOf(failure.getMessage()));
            scheduler.asyncAfter(delay, () -> write(id, written));
            return true;
        }
        log.error(
                "event=vault_write_gave_up owner=" + id.owner() + " index=" + id.index() + " after " + written.retries
                        + " retries",
                failure);
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

    /** Write a snapshot, returning the row's stamp after the write, or empty when the vault was gone. */
    private Optional<VaultStamp> store(Closed<W> written) {
        Window<W> window = written.window;
        if (window.staffView()) {
            return Optional.of(saveVault.keep(window.owner(), window.vault(), written.contents, written.at));
        }
        return saveVault.save(window.owner(), window.vault(), written.contents, written.at);
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
        settled.forEach(id -> {
            closed.remove(id);
            landedHere.remove(id);
        });
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

    /** The items of one snapshot of a vault, and where they are. Guarded by the {@link VaultWindows} lock. */
    private static final class Closed<W> {
        private final Window<W> window;
        private final VaultContents contents;
        private final Instant at;
        private State state;
        private @Nullable VaultStamp landed;
        private @Nullable Instant settledAt;
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
