package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveResult;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rules every {@code /invsee} and {@code /endersee} window follows, online or offline, so that an edit can
 * neither duplicate nor lose an item. The item type is a parameter, so the rules run without a server.
 *
 * <p>A window is a copy. The staff member moves items between it and their own inventory at once, but the target only
 * changes when the window closes, so between the two the staff member's inventory is ahead of the target. The rules
 * keep the two in step:
 *
 * <ul>
 *   <li><b>One editor per container.</b> Online and offline windows share one editor slot per player and container,
 *       taken before the container is read. Anyone else opening it meanwhile gets it view only, so two people never
 *       take the same items.
 *   <li><b>A save is a difference.</b> Only the slots that differ from what the window opened with are written, and
 *       only where the container still holds what the window opened with. A slot someone changed in between is a
 *       conflict and is never written over.
 *   <li><b>What is not saved is taken back.</b> For every slot whose change was not saved, what the staff member took
 *       out of it is taken back from their inventory and what they put into it is returned to them. The other slots
 *       are still saved only when the staff member is known to hold everything that has to be taken back: a move from
 *       a conflicting slot to another slot of the window holds nothing, so writing its other half would copy the
 *       item. What cannot be taken back is logged with the full item data.
 *   <li><b>Items stay put while an edit is open.</b> {@link #holding} tells the item guard to keep the staff
 *       member's items where they are from the open until the edit is settled, so what may have to be taken back is
 *       still there. A drop is only refused when the item fits back into their inventory ({@link #refusesDrop}).
 *   <li><b>A window that never appears edits nothing.</b> The menu builds a window off the tick, and another plugin
 *       may refuse it. Until it appears the edit is pending; one refused, or not there after {@link #OPEN_WAIT}, is
 *       dropped and frees its editor slot, and a window that turns up after that is refused.
 *   <li><b>Some closes cannot wait.</b> When the staff member disconnects the server saves their inventory right
 *       after the window closes, and when the module stops nothing scheduled runs any more. In both cases the save
 *       and the taking back happen at once, on the closing thread. A save that throws is settled as failed.
 * </ul>
 *
 * @param <I> the item type
 */
@NullMarked
final class MirrorEdits<I> {

    /** How long a reopen waits for the same container's save, and a leaving staff member for theirs. */
    static final Duration WAIT = Duration.ofSeconds(3);

    /**
     * How long a window may take to appear. The menu builds it off the tick, so it shows up a little after it is
     * asked for; one that has not appeared by then never will, or another plugin refused it.
     */
    static final Duration OPEN_WAIT = Duration.ofSeconds(10);

    /** Why a window closed. */
    enum Cause {
        /** The staff member closed it. The save may take its time. */
        CLOSED,
        /** The staff member is disconnecting; the server saves their inventory right after. */
        DISCONNECT,
        /** The module is stopping; nothing scheduled will run. */
        STOP
    }

    /** How items compare and count. */
    interface Items<I> {

        /** Whether {@code item} stands for an empty slot. */
        boolean nothing(@Nullable I item);

        /** The same item in the same amount. */
        boolean same(I a, I b);

        /** The same item in any amount. */
        boolean similar(I a, I b);

        int amount(I item);

        I withAmount(I item, int amount);

        /** Everything about {@code item}, enough to make it again by hand. */
        String describe(I item);
    }

    /** The targets' live containers. Every call but {@link #online} runs on the target's own thread. */
    interface Targets<I> {

        boolean online(PlayerRef player);

        List<@Nullable I> contents(PlayerRef target, MirrorKind kind);

        @Nullable I slot(PlayerRef target, MirrorKind kind, int slot);

        void set(PlayerRef target, MirrorKind kind, int slot, @Nullable I item);
    }

    /** The staff members: their inventory and their screen. Every call but {@link #online} runs on their thread. */
    interface Viewers<I> {

        boolean online(PlayerRef viewer);

        /** Take {@code items} out of the viewer's inventory; returns what was not there to take. */
        List<I> take(PlayerRef viewer, List<I> items);

        /** Give {@code items} to the viewer, dropping at their feet what does not fit. */
        void give(PlayerRef viewer, List<I> items);

        /** Whether {@code item} fits into the viewer's inventory as it is now. */
        boolean fits(PlayerRef viewer, I item);

        /** Ask for {@code edit}'s window to be shown to its viewer; it may appear later, or never. */
        void open(Edit<I> edit);

        /** What {@code edit}'s window holds now, or empty when the viewer does not have it open. */
        Optional<List<@Nullable I>> window(Edit<I> edit);

        /** Close {@code edit}'s window if it is open, and forget it. */
        void close(Edit<I> edit);
    }

    /** The stored containers of players who are not online. */
    interface Stored<I> {

        OfflineSnapshot<I> read(UUID player, MirrorKind kind);

        SaveResult save(
                UUID player,
                MirrorKind kind,
                OfflineSnapshot<I> base,
                Map<Integer, @Nullable I> changes,
                Predicate<Set<Integer>> partly);
    }

    private enum State {
        /** The window was asked for and has not appeared yet. */
        PENDING,
        OPEN,
        SAVING,
        DONE
    }

    /** One player's inventory or ender chest. */
    private record Container(UUID player, MirrorKind kind) {}

    /** The editor slot of one container. Released once the container's save has an outcome. */
    private static final class Lease {

        private final PlayerRef editor;
        private final CompletableFuture<Void> released = new CompletableFuture<>();

        Lease(PlayerRef editor) {
            this.editor = editor;
        }
    }

    /** One window, from its open until its edit is settled. */
    static final class Edit<I> {

        private final PlayerRef viewer;
        private final PlayerRef target;
        private final MirrorKind kind;
        private final List<@Nullable I> base;
        private final @Nullable OfflineSnapshot<I> stored;
        private final @Nullable Lease lease;
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final AtomicBoolean started = new AtomicBoolean();
        private final CompletableFuture<SaveResult> result = new CompletableFuture<>();
        private volatile @Nullable Supplier<SaveResult> work;
        private volatile List<@Nullable I> contents = List.of();
        private volatile List<Integer> changed = List.of();
        private final Set<Integer> written = ConcurrentHashMap.newKeySet();

        private Edit(
                PlayerRef viewer,
                PlayerRef target,
                MirrorKind kind,
                List<@Nullable I> base,
                @Nullable OfflineSnapshot<I> stored,
                @Nullable Lease lease) {
            this.viewer = viewer;
            this.target = target;
            this.kind = kind;
            this.base = copyOf(base);
            this.stored = stored;
            this.lease = lease;
        }

        PlayerRef viewer() {
            return viewer;
        }

        PlayerRef target() {
            return target;
        }

        MirrorKind kind() {
            return kind;
        }

        /** The items the window opened with. */
        List<@Nullable I> base() {
            return base;
        }

        /** Whether the window was opened to change the container; a view-only window refuses every move. */
        boolean editable() {
            return lease != null;
        }

        /** Whether the window may still be changed: it is editable and its contents have not been handed over. */
        boolean accepting() {
            State now = state.get();
            return editable() && (now == State.PENDING || now == State.OPEN);
        }

        private Edit<I> viewOnly() {
            return new Edit<>(viewer, target, kind, base, stored, null);
        }
    }

    private final Scheduler scheduler;
    private final Items<I> items;
    private final Targets<I> targets;
    private final Viewers<I> viewers;
    private final Stored<I> stored;
    private final Notifier notifier;
    private final Logger log;
    private final Set<Edit<I>> edits = ConcurrentHashMap.newKeySet();
    private final Map<Container, Lease> leases = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    MirrorEdits(
            Scheduler scheduler,
            Items<I> items,
            Targets<I> targets,
            Viewers<I> viewers,
            Stored<I> stored,
            Notifier notifier,
            Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.items = Objects.requireNonNull(items, "items");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.stored = Objects.requireNonNull(stored, "stored");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    // ---- opening ----------------------------------------------------------------------------------------------

    /** Open an online target's container: the editor slot and the snapshot are taken on the target's thread. */
    void openOnline(PlayerRef viewer, PlayerRef target, MirrorKind kind, boolean wantsEdit) {
        Container container = new Container(target.uuid(), kind);
        scheduler.onEntity(target, () -> {
            if (!targets.online(target)) {
                return;
            }
            Lease lease = wantsEdit ? lease(container, viewer) : null;
            PlayerRef heldBy = wantsEdit && lease == null ? editorOf(container) : null;
            List<@Nullable I> contents = targets.contents(target, kind);
            show(new Edit<>(viewer, target, kind, contents, null, lease), container, heldBy);
        });
    }

    /** Open an offline target's container: the editor slot is taken before the file is read, on the async lane. */
    void openOffline(PlayerRef viewer, PlayerRef target, MirrorKind kind, boolean wantsEdit) {
        Container container = new Container(target.uuid(), kind);
        scheduler.async(() -> {
            Lease lease = wantsEdit ? leaseAfterSave(container, viewer) : null;
            PlayerRef heldBy = wantsEdit && lease == null ? editorOf(container) : null;
            OfflineSnapshot<I> snapshot = stored.read(target.uuid(), kind);
            SaveOutcome problem = snapshot.problem().orElse(null);
            if (problem != null) {
                release(container, lease);
                notifier.send(viewer, readProblem(problem), Map.of("player", target.name()));
                return;
            }
            if (lease != null && !snapshot.editable()) {
                release(container, lease);
                lease = null;
                notifier.send(viewer, PlayerstateMessageKey.OFFLINE_VIEW_ONLY_OLD, Map.of("player", target.name()));
            }
            show(new Edit<>(viewer, target, kind, Arrays.asList(snapshot.items()), snapshot, lease), container, heldBy);
        });
    }

    private void show(Edit<I> edit, Container container, @Nullable PlayerRef heldBy) {
        scheduler.onEntity(
                edit.viewer,
                () -> {
                    if (stopping || !viewers.online(edit.viewer)) {
                        release(container, edit.lease);
                        return;
                    }
                    // A second editable window while the last edit is still being saved could take in what that
                    // save may have to take back.
                    boolean settling = edit.editable() && settling(edit.viewer.uuid());
                    Edit<I> shown = edit;
                    if (settling) {
                        release(container, edit.lease);
                        shown = edit.viewOnly();
                    }
                    edits.add(shown);
                    viewers.open(shown);
                    Edit<I> asked = shown;
                    scheduler.laterEntity(edit.viewer, OPEN_WAIT, () -> unlessShown(asked));
                    if (settling) {
                        notifier.send(edit.viewer, PlayerstateMessageKey.MIRROR_VIEW_ONLY_SAVING, Map.of());
                    } else if (heldBy != null) {
                        notifier.send(
                                edit.viewer,
                                PlayerstateMessageKey.MIRROR_VIEW_ONLY_EDITED,
                                Map.of("editor", heldBy.name()));
                    }
                },
                () -> release(container, edit.lease));
    }

    /**
     * Whether the window of {@code edit} may appear now. A window that turns up after its edit was given up on would
     * let items move that nothing saves, so it is refused.
     */
    boolean opening(Edit<I> edit) {
        State now = edit.state.get();
        return now == State.PENDING || now == State.OPEN;
    }

    /** The window of {@code edit} appeared. */
    void shown(Edit<I> edit) {
        edit.state.compareAndSet(State.PENDING, State.OPEN);
    }

    /** The window of {@code edit} was refused, by another plugin or by {@link #opening}: nothing was edited. */
    void notShown(Edit<I> edit) {
        if (edit.state.compareAndSet(State.PENDING, State.DONE)) {
            forget(edit);
        }
    }

    private void unlessShown(Edit<I> edit) {
        if (edit.state.get() != State.PENDING) {
            return;
        }
        if (viewers.window(edit).isPresent()) {
            shown(edit);
        } else {
            notShown(edit);
        }
    }

    /** Drop an edit that never handed anything over: its editor slot is free again. */
    private void forget(Edit<I> edit) {
        edits.remove(edit);
        release(new Container(edit.target.uuid(), edit.kind), edit.lease);
        viewers.close(edit);
    }

    private boolean settling(UUID viewer) {
        for (Edit<I> edit : edits) {
            if (edit.viewer.uuid().equals(viewer) && edit.editable() && edit.state.get() == State.SAVING) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Lease lease(Container container, PlayerRef viewer) {
        Lease lease = new Lease(viewer);
        return leases.putIfAbsent(container, lease) == null ? lease : null;
    }

    /** Take the editor slot, first waiting a moment for a save of the container that is already under way. */
    private @Nullable Lease leaseAfterSave(Container container, PlayerRef viewer) {
        Lease held = leases.get(container);
        if (held != null && saving(container)) {
            try {
                held.released.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException | ExecutionException slow) {
                // Opens view only below; the editor slot is still taken.
            }
        }
        return lease(container, viewer);
    }

    private boolean saving(Container container) {
        Lease lease = leases.get(container);
        for (Edit<I> edit : edits) {
            if (edit.lease == lease && edit.state.get() == State.SAVING) {
                return true;
            }
        }
        return false;
    }

    private @Nullable PlayerRef editorOf(Container container) {
        Lease lease = leases.get(container);
        return lease == null ? null : lease.editor;
    }

    private void release(Container container, @Nullable Lease lease) {
        if (lease != null) {
            leases.remove(container, lease);
            lease.released.complete(null);
        }
    }

    // ---- closing ----------------------------------------------------------------------------------------------

    /**
     * {@code edit}'s window closed holding {@code contents}. Called on the viewer's thread, once per window; a second
     * call for the same window does nothing.
     */
    void closed(Edit<I> edit, List<@Nullable I> contents, Cause cause) {
        if (!edit.state.compareAndSet(State.OPEN, State.SAVING)
                && !edit.state.compareAndSet(State.PENDING, State.SAVING)) {
            return;
        }
        Container container = new Container(edit.target.uuid(), edit.kind);
        edit.contents = copyOf(contents);
        edit.changed = changed(edit.base, edit.contents);
        if (!edit.editable() || edit.changed.isEmpty()) {
            release(container, edit.lease);
            settle(edit);
            return;
        }
        boolean now = cause != Cause.CLOSED || stopping;
        if (targets.online(edit.target)) {
            edit.work = () -> applyLive(edit);
            if (now) {
                edit.started.set(true);
                deliver(
                        edit,
                        container,
                        scheduler.ownsEntity(edit.target)
                                ? run(edit, () -> applyLive(edit))
                                : SaveResult.of(SaveOutcome.PLAYER_PRESENT));
                conclude(edit);
            } else {
                scheduler.onEntity(edit.target, () -> runOnce(edit, container), () -> {
                    if (edit.started.compareAndSet(false, true)) {
                        deliver(edit, container, SaveResult.of(SaveOutcome.PLAYER_PRESENT));
                    }
                    afterResult(edit);
                });
            }
        } else if (edit.stored == null) {
            // Opened on a live container whose owner has left since: their data was saved without this edit.
            deliver(edit, container, SaveResult.of(SaveOutcome.PLAYER_PRESENT));
            conclude(edit);
        } else {
            OfflineSnapshot<I> base = edit.stored;
            edit.work = () -> stored.save(
                    edit.target.uuid(), edit.kind, base, changes(edit), conflicts -> partly(edit, conflicts));
            if (now) {
                edit.started.set(true);
                deliver(edit, container, run(edit, Objects.requireNonNull(edit.work)));
                conclude(edit);
            } else {
                scheduler.async(() -> runOnce(edit, container));
            }
        }
    }

    /** Run the edit's save once, wherever it is first asked for, then hand the outcome to the viewer's thread. */
    private void runOnce(Edit<I> edit, Container container) {
        if (edit.started.compareAndSet(false, true)) {
            deliver(edit, container, run(edit, Objects.requireNonNull(edit.work)));
        }
        afterResult(edit);
    }

    /**
     * Run a save. One that throws still has an outcome, so the edit is settled and its editor slot freed; a live save
     * that wrote some slots before it threw counts as saved for those.
     */
    private SaveResult run(Edit<I> edit, Supplier<SaveResult> work) {
        try {
            return work.get();
        } catch (RuntimeException | LinkageError failure) {
            log.error(
                    "event=mirror_edit_save_failed viewer=" + edit.viewer.name() + " target=" + edit.target.name()
                            + " container=" + edit.kind.label(),
                    failure);
            Set<Integer> written = Set.copyOf(edit.written);
            if (written.isEmpty()) {
                return SaveResult.of(SaveOutcome.FAILED);
            }
            Set<Integer> unwritten = new HashSet<>(edit.changed);
            unwritten.removeAll(written);
            return new SaveResult(SaveOutcome.SAVED, Set.copyOf(unwritten));
        }
    }

    private void afterResult(Edit<I> edit) {
        scheduler.onEntity(edit.viewer, () -> conclude(edit), () -> lost(edit));
    }

    /** The save has an outcome: the container is final, so the next editor may take it. */
    private void deliver(Edit<I> edit, Container container, SaveResult result) {
        edit.result.complete(result);
        release(container, edit.lease);
    }

    /**
     * Settle every edit of {@code viewer} still being saved, now, while the viewer is still here: a save not started
     * yet runs on this thread, one under way is waited for.
     */
    void quitting(PlayerRef viewer) {
        for (Edit<I> edit : List.copyOf(edits)) {
            if (!edit.viewer.uuid().equals(viewer.uuid())) {
                continue;
            }
            State now = edit.state.get();
            if (now == State.SAVING) {
                finishNow(edit);
            } else if (now == State.PENDING) {
                notShown(edit);
            }
        }
    }

    private void finishNow(Edit<I> edit) {
        Container container = new Container(edit.target.uuid(), edit.kind);
        Supplier<SaveResult> work = edit.work;
        boolean live = targets.online(edit.target);
        if (work != null && (!live || scheduler.ownsEntity(edit.target)) && edit.started.compareAndSet(false, true)) {
            deliver(edit, container, run(edit, work));
        }
        try {
            edit.result.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (TimeoutException | ExecutionException slow) {
            return; // the outcome lands after the viewer left and is logged then
        }
        conclude(edit);
    }

    /**
     * Stop: every open window is saved and closed now, and every save under way is finished, each on its viewer's
     * thread or, once nothing scheduled runs, right here.
     */
    void stop() {
        stopping = true;
        for (Edit<I> edit : List.copyOf(edits)) {
            Runnable flush = () -> flush(edit);
            // Right here when this thread may touch the viewer: a reload unregisters the item guard as soon as this
            // returns, so no edit may be left being saved.
            if (scheduler.ownsEntity(edit.viewer)) {
                flush.run();
            } else {
                scheduler.onEntity(edit.viewer, flush, flush);
            }
        }
    }

    private void flush(Edit<I> edit) {
        State now = edit.state.get();
        if (now == State.SAVING) {
            finishNow(edit);
            return;
        }
        Optional<List<@Nullable I>> window = viewers.window(edit);
        if (now == State.PENDING && window.isEmpty()) {
            notShown(edit);
            return;
        }
        viewers.close(edit);
        closed(edit, window.orElse(edit.base), Cause.STOP);
    }

    // ---- concluding -------------------------------------------------------------------------------------------

    /** On the viewer's thread: take back what was not saved, and tell them. */
    private void conclude(Edit<I> edit) {
        SaveResult result = outcomeOf(edit);
        if (result == null || !edit.state.compareAndSet(State.SAVING, State.DONE)) {
            return;
        }
        edits.remove(edit);
        boolean saved = result.outcome() == SaveOutcome.SAVED;
        List<Integer> undone = saved ? sorted(result.conflicts()) : edit.changed;
        if (undone.isEmpty()) {
            return;
        }
        Transfer<I> transfer = transfer(edit, undone);
        List<I> missing = viewers.take(edit.viewer, transfer.take());
        viewers.give(edit.viewer, transfer.give());
        Map<String, String> target = Map.of("player", edit.target.name());
        boolean partly = saved && !result.conflicts().containsAll(edit.changed);
        notifier.send(edit.viewer, partly ? PlayerstateMessageKey.MIRROR_PARTLY_SAVED : undoneKey(result), target);
        if (!missing.isEmpty()) {
            notifier.send(edit.viewer, PlayerstateMessageKey.MIRROR_NOT_RECLAIMED, target);
            log.warn(
                    "event=mirror_edit_not_reclaimed viewer={} target={} container={} outcome={} not_taken_back=[{}]",
                    edit.viewer.name(),
                    edit.target.name(),
                    edit.kind.label(),
                    result.outcome().name(),
                    describe(missing));
        }
    }

    /** The viewer left before the outcome came back, so nothing can be taken back: log what should have been. */
    private void lost(Edit<I> edit) {
        SaveResult result = outcomeOf(edit);
        if (result == null || !edit.state.compareAndSet(State.SAVING, State.DONE)) {
            return;
        }
        edits.remove(edit);
        boolean saved = result.outcome() == SaveOutcome.SAVED;
        List<Integer> undone = saved ? sorted(result.conflicts()) : edit.changed;
        if (undone.isEmpty()) {
            return;
        }
        Transfer<I> transfer = transfer(edit, undone);
        log.warn(
                "event=offline_edit_not_saved viewer={} target={} container={} outcome={} viewer_holds_extra=[{}] "
                        + "viewer_is_owed=[{}]",
                edit.viewer.name(),
                edit.target.name(),
                edit.kind.label(),
                result.outcome().name(),
                describe(transfer.take()),
                describe(transfer.give()));
    }

    private static @Nullable SaveResult outcomeOf(Edit<?> edit) {
        return edit.result.isDone() ? edit.result.join() : null;
    }

    private void settle(Edit<I> edit) {
        edit.state.set(State.DONE);
        edits.remove(edit);
    }

    // ---- the live container -----------------------------------------------------------------------------------

    /** On the target's thread: write the changed slots that still hold what the window opened with. */
    private SaveResult applyLive(Edit<I> edit) {
        if (!targets.online(edit.target)) {
            return SaveResult.of(SaveOutcome.PLAYER_PRESENT);
        }
        List<Integer> conflicts = new ArrayList<>();
        for (int slot : edit.changed) {
            if (!sameSlot(targets.slot(edit.target, edit.kind, slot), edit.base.get(slot))) {
                conflicts.add(slot);
            }
        }
        if (!conflicts.isEmpty() && !partly(edit, Set.copyOf(conflicts))) {
            return new SaveResult(SaveOutcome.CHANGED, Set.copyOf(conflicts));
        }
        for (int slot : edit.changed) {
            if (!conflicts.contains(slot)) {
                targets.set(edit.target, edit.kind, slot, edit.contents.get(slot));
                edit.written.add(slot);
            }
        }
        return new SaveResult(SaveOutcome.SAVED, Set.copyOf(conflicts));
    }

    // ---- the arithmetic ---------------------------------------------------------------------------------------

    /** What moving the window's changes in {@code slots} back undoes: what to take from the viewer and give them. */
    record Transfer<I>(List<I> take, List<I> give) {}

    private List<Integer> changed(List<@Nullable I> base, List<@Nullable I> contents) {
        List<Integer> changed = new ArrayList<>();
        for (int slot = 0; slot < base.size(); slot++) {
            if (!sameSlot(base.get(slot), slot < contents.size() ? contents.get(slot) : null)) {
                changed.add(slot);
            }
        }
        return List.copyOf(changed);
    }

    private Map<Integer, @Nullable I> changes(Edit<I> edit) {
        Map<Integer, @Nullable I> changes = new HashMap<>();
        for (int slot : edit.changed) {
            changes.put(slot, edit.contents.get(slot));
        }
        return changes;
    }

    /**
     * Whether the window's other slots may still be saved when {@code conflicts} cannot: only when the viewer holds
     * everything that undoing the conflicting slots takes back. They hold, at most, what the whole edit moved out of
     * the window, so that is the bound.
     */
    private boolean partly(Edit<I> edit, Set<Integer> conflicts) {
        List<I> needed = transfer(edit, sorted(conflicts)).take();
        List<I> held = transfer(edit, edit.changed).take();
        for (I item : needed) {
            int have = 0;
            for (I candidate : held) {
                if (items.similar(candidate, item)) {
                    have += items.amount(candidate);
                }
            }
            if (have < items.amount(item)) {
                return false;
            }
        }
        return true;
    }

    /** The net of moving {@code slots} back from the window's contents to what it opened with. */
    private Transfer<I> transfer(Edit<I> edit, List<Integer> slots) {
        List<I> kinds = new ArrayList<>();
        List<Integer> net = new ArrayList<>();
        for (int slot : slots) {
            count(kinds, net, edit.base.get(slot), 1);
            count(kinds, net, slot < edit.contents.size() ? edit.contents.get(slot) : null, -1);
        }
        List<I> take = new ArrayList<>();
        List<I> give = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            int amount = net.get(i);
            if (amount > 0) {
                take.add(items.withAmount(kinds.get(i), amount));
            } else if (amount < 0) {
                give.add(items.withAmount(kinds.get(i), -amount));
            }
        }
        return new Transfer<>(List.copyOf(take), List.copyOf(give));
    }

    private void count(List<I> kinds, List<Integer> net, @Nullable I item, int sign) {
        if (item == null || items.nothing(item)) {
            return;
        }
        for (int i = 0; i < kinds.size(); i++) {
            if (items.similar(kinds.get(i), item)) {
                net.set(i, net.get(i) + sign * items.amount(item));
                return;
            }
        }
        kinds.add(item);
        net.add(sign * items.amount(item));
    }

    private boolean sameSlot(@Nullable I a, @Nullable I b) {
        if (a == null || items.nothing(a)) {
            return b == null || items.nothing(b);
        }
        return b != null && !items.nothing(b) && items.same(a, b);
    }

    private String describe(List<I> list) {
        return list.stream().map(items::describe).collect(Collectors.joining(", "));
    }

    private static List<Integer> sorted(Set<Integer> slots) {
        return slots.stream().sorted().toList();
    }

    // ---- what the rest of the plugin asks -----------------------------------------------------------------------

    /** Whether {@code viewer} has an edit that is open or not yet settled, so their items must stay where they are. */
    boolean holding(UUID viewer) {
        for (Edit<I> edit : edits) {
            if (edit.viewer.uuid().equals(viewer) && edit.editable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a drop of {@code item} by {@code viewer} is refused. Refusing hands the item back into the inventory,
     * so a drop is only refused when it fits there: the server also drops what a closing window hands back to a full
     * inventory, and refusing that would delete it. A drop let through while an edit is open is logged.
     */
    boolean refusesDrop(PlayerRef viewer, I item) {
        if (!holding(viewer.uuid())) {
            return false;
        }
        if (viewers.fits(viewer, item)) {
            return true;
        }
        log.warn(
                "event=mirror_drop_let_through viewer={} item=[{}]; the inventory had no room to take it back",
                viewer.name(),
                items.describe(item));
        return false;
    }

    /** The windows open or being saved. */
    int openCount() {
        return edits.size();
    }

    private static PlayerstateMessageKey readProblem(SaveOutcome problem) {
        return switch (problem) {
            case PLAYER_PRESENT -> PlayerstateMessageKey.OFFLINE_JOINING;
            case NO_DATA -> PlayerstateMessageKey.OFFLINE_NO_DATA;
            case UNAVAILABLE -> PlayerstateMessageKey.OFFLINE_UNAVAILABLE;
            case SAVED, CHANGED, FAILED -> PlayerstateMessageKey.OFFLINE_READ_FAILED;
        };
    }

    private static PlayerstateMessageKey undoneKey(SaveResult result) {
        return switch (result.outcome()) {
            case PLAYER_PRESENT -> PlayerstateMessageKey.MIRROR_UNDONE_PRESENT;
            // A save that wrote nothing because every slot it changed conflicted counts as changed.
            case SAVED, CHANGED -> PlayerstateMessageKey.MIRROR_UNDONE_CHANGED;
            case NO_DATA, FAILED, UNAVAILABLE -> PlayerstateMessageKey.MIRROR_UNDONE_FAILED;
        };
    }

    /** An unmodifiable copy that keeps empty slots, which {@link List#copyOf} refuses. */
    private static <T> List<@Nullable T> copyOf(List<@Nullable T> list) {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }
}
