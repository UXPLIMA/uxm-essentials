package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemHandBack;
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
 * own executor and its own shutdown, for no difference a player could notice. A write that throws hands its items back
 * rather than leaving them only in memory.
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

    private final SaveVault saveVault;
    private final VaultItemHandBack handBack;
    private final Clock clock;
    private final Logger log;
    private final Map<VaultId, Window<W>> open = new HashMap<>();
    private final Map<VaultId, Closed<W>> closed = new HashMap<>();
    private final Object[] writeLocks = new Object[WRITE_LOCKS];
    private int writesUnderWay;

    public VaultWindows(SaveVault saveVault, VaultItemHandBack handBack, Clock clock, Logger log) {
        this.saveVault = Objects.requireNonNull(saveVault, "saveVault");
        this.handBack = Objects.requireNonNull(handBack, "handBack");
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
        try {
            synchronized (writeLocks[Math.floorMod(id.hashCode(), WRITE_LOCKS)]) {
                synchronized (this) {
                    if (closed.get(id) != written) {
                        return; // a later snapshot of this vault holds these items and writes them
                    }
                }
                Optional<VaultStamp> landed;
                try {
                    landed = store(written);
                } catch (RuntimeException failure) {
                    failed(id, written, failure);
                    return;
                }
                boolean giveBack = false;
                synchronized (this) {
                    written.settledAt = clock.instant();
                    if (landed.isPresent()) {
                        written.landed = landed.get();
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
        } finally {
            synchronized (this) {
                writesUnderWay--;
                notifyAll();
            }
        }
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
     * A write that threw: whether it reached the database is unknown, and the items must not live only in memory. A
     * snapshot nobody took over goes back to the owner (their inventory, or kept for their next join); one a reopened
     * window holds stays with that window. Either way it is forgotten, so the next reopen reads the store.
     */
    private void failed(VaultId id, Closed<W> written, RuntimeException failure) {
        boolean giveBack;
        synchronized (this) {
            giveBack = written.state == State.PENDING;
            if (closed.get(id) == written) {
                closed.remove(id);
            }
        }
        log.error(
                "event=vault_write_failed owner=" + written.window.owner().uuid() + " index=" + id.index()
                        + (giveBack ? " (the items are handed back)" : " (a reopened window holds the items)"),
                failure);
        if (giveBack && !written.contents.isEmpty()) {
            handBack.handBack(written.window.owner(), written.contents);
        }
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
