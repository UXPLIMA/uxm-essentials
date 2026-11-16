package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveResult;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.StoredSlots;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * An {@code /invsee} or {@code /endersee} edit, online or offline, through to its outcome: nothing it moves is ever
 * doubled or lost, whether it is saved, saved in part or not saved at all, and whatever ends the window.
 */
class MirrorEditsTest {

    private static final PlayerRef ANN = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Ann");
    private static final PlayerRef BEN = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Ben");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    private static final Stack DIAMONDS = new Stack("diamond", 3);
    private static final Stack GOLD = new Stack("gold", 2);
    private static final Stack APPLE = new Stack("apple", 1);

    private final ManualScheduler scheduler = new ManualScheduler();
    private final World world = new World();
    private final Files files = new Files();
    private final List<String> told = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final MirrorEdits<Stack> edits = new MirrorEdits<>(
            scheduler,
            new StackRules(),
            world,
            world,
            files,
            new Notifier((viewer, key, values) -> key.key(), (viewer, text) -> told.add(viewer.name() + ": " + text)),
            new Warnings(warnings));

    // ---- saved -------------------------------------------------------------------------------------------------

    @Test
    void anOfflineEditIsSavedOnTheAsyncLaneAsTheSlotsItChanged() {
        files.put(SAM, slots(DIAMONDS, null, APPLE));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);

        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);

        assertThat(scheduler.pendingAsync())
                .as("the save waits for the async lane")
                .isEqualTo(1);
        scheduler.runAsync();
        assertThat(files.slots(SAM)).containsExactly(null, null, APPLE);
        assertThat(world.inventory(ANN)).containsExactly(DIAMONDS);
        assertThat(told).isEmpty();
    }

    @Test
    void anEditOfAnOnlineTargetChangesOnlyTheSlotsTheWindowChanged() {
        world.join(SAM, slots(DIAMONDS, null, APPLE));
        MirrorEdits.Edit<Stack> ann = openOnline(ANN, true);
        world.live(SAM).set(2, GOLD);

        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);

        assertThat(world.live(SAM))
                .as("the target's own change to slot 2 stays")
                .containsExactly(null, null, GOLD);
        assertThat(world.inventory(ANN)).containsExactly(DIAMONDS);
    }

    // ---- one editor --------------------------------------------------------------------------------------------

    @Test
    void theEditorSlotIsTakenBeforeTheReadAndASecondOpenIsViewOnly() {
        files.put(SAM, slots(DIAMONDS));
        edits.openOffline(ANN, SAM, MirrorKind.INVENTORY, true);
        edits.openOffline(BEN, SAM, MirrorKind.INVENTORY, true);

        scheduler.runAsync();

        assertThat(world.opened(ANN).editable()).isTrue();
        assertThat(world.opened(BEN).editable()).isFalse();
        assertThat(told).containsExactly("Ben: playerstate.mirror.view-only-edited");
        assertThat(edits.holding(BEN.uuid()))
                .as("a view-only window holds nothing back")
                .isFalse();
    }

    @Test
    void onlineAndOfflineWindowsShareTheEditorSlot() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        world.join(SAM, slots(DIAMONDS));

        MirrorEdits.Edit<Stack> ben = openOnline(BEN, true);

        assertThat(ann.editable()).isTrue();
        assertThat(ben.editable()).isFalse();
    }

    // ---- not saved ---------------------------------------------------------------------------------------------

    @Test
    void aConflictingEditIsUndoneAndWhatTheViewerTookIsTakenBack() {
        files.put(SAM, slots(DIAMONDS, null));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        files.slots(SAM).set(0, null);

        close(ann, MirrorEdits.Cause.CLOSED);
        scheduler.runAsync();

        assertThat(world.inventory(ANN))
                .as("someone else took the diamonds first")
                .isEmpty();
        assertThat(files.slots(SAM)).containsExactly(null, null);
        assertThat(told).containsExactly("Ann: playerstate.mirror.undone-changed");
        assertThat(edits.holding(ANN.uuid())).isFalse();
    }

    @Test
    void aMoveOutOfAConflictingSlotWritesNeitherHalf() {
        files.put(SAM, slots(DIAMONDS, null));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        world.screen(ann).set(0, null);
        world.screen(ann).set(1, DIAMONDS);
        files.slots(SAM).set(0, APPLE);

        close(ann, MirrorEdits.Cause.CLOSED);
        scheduler.runAsync();

        assertThat(files.slots(SAM))
                .as("writing slot 1 alone would copy the diamonds")
                .containsExactly(APPLE, null);
        assertThat(world.inventory(ANN)).isEmpty();
        assertThat(told).containsExactly("Ann: playerstate.mirror.undone-changed");
    }

    @Test
    void theRestOfAnEditIsSavedWhenTheViewerHoldsWhatTheConflictTakesBack() {
        files.put(SAM, slots(DIAMONDS, null));
        world.give(ANN, GOLD);
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        putIn(ann, 1, GOLD);
        files.slots(SAM).set(0, null);

        close(ann, MirrorEdits.Cause.CLOSED);
        scheduler.runAsync();

        assertThat(files.slots(SAM)).containsExactly(null, GOLD);
        assertThat(world.inventory(ANN))
                .as("the diamonds were taken first by someone else")
                .isEmpty();
        assertThat(told).containsExactly("Ann: playerstate.mirror.partly-saved");
    }

    @Test
    void anOnlineTargetsConflictingSlotIsUndone() {
        world.join(SAM, slots(DIAMONDS, null));
        world.give(ANN, GOLD);
        MirrorEdits.Edit<Stack> ann = openOnline(ANN, true);
        takeOut(ann, 0);
        putIn(ann, 0, GOLD);
        world.live(SAM).set(0, APPLE);

        close(ann, MirrorEdits.Cause.CLOSED);

        assertThat(world.live(SAM)).containsExactly(APPLE, null);
        assertThat(world.inventory(ANN))
                .as("the gold came back, the diamonds went")
                .containsExactly(GOLD);
        assertThat(told).containsExactly("Ann: playerstate.mirror.undone-changed");
    }

    @Test
    void anOnlineWindowWhoseTargetLeftIsUndone() {
        world.join(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOnline(ANN, true);
        takeOut(ann, 0);
        world.leave(SAM);

        close(ann, MirrorEdits.Cause.CLOSED);

        assertThat(world.inventory(ANN)).isEmpty();
        assertThat(told).containsExactly("Ann: playerstate.mirror.undone-present");
    }

    @Test
    void whatCannotBeTakenBackIsLoggedWithTheItem() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        world.take(ANN, List.of(DIAMONDS));
        files.failing = true;

        close(ann, MirrorEdits.Cause.CLOSED);
        scheduler.runAsync();

        assertThat(told)
                .containsExactly("Ann: playerstate.mirror.undone-failed", "Ann: playerstate.mirror.not-reclaimed");
        assertThat(warnings).singleElement().asString().contains("event=mirror_edit_not_reclaimed", "diamond x3");
    }

    // ---- items stay put ----------------------------------------------------------------------------------------

    @Test
    void theViewerCannotDropItemsFromTheOpenUntilTheSaveComesBack() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        assertThat(edits.holding(ANN.uuid())).isTrue();

        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);
        assertThat(edits.holding(ANN.uuid())).as("the save has not come back").isTrue();

        scheduler.runAsync();
        assertThat(edits.holding(ANN.uuid())).isFalse();
    }

    @Test
    void aDropIsRefusedOnlyWhenTheItemFitsBackIntoTheInventory() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);

        assertThat(edits.refusesDrop(ANN, APPLE)).isTrue();

        world.full.add(ANN.uuid());
        assertThat(edits.refusesDrop(ANN, APPLE))
                .as("a refused drop goes back into the inventory, so refusing one that cannot would delete it")
                .isFalse();
        assertThat(warnings).singleElement().asString().contains("event=mirror_drop_let_through", "apple x1");

        scheduler.runAsync();
        world.full.clear();
        assertThat(edits.refusesDrop(ANN, APPLE)).as("the edit is settled").isFalse();
    }

    @Test
    void aViewerWithASaveUnderWayOpensTheNextWindowViewOnly() {
        files.put(SAM, slots(DIAMONDS));
        files.put(BEN, slots(GOLD));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);

        edits.openOffline(ANN, BEN, MirrorKind.INVENTORY, true);
        scheduler.runLastAsync();

        assertThat(world.opened(ANN).target()).isEqualTo(BEN);
        assertThat(world.opened(ANN).editable()).isFalse();
        assertThat(told).containsExactly("Ann: playerstate.mirror.view-only-saving");

        scheduler.runAsync();
        assertThat(files.slots(SAM)).containsExactly((Stack) null);
        edits.openOffline(ANN, BEN, MirrorKind.INVENTORY, true);
        scheduler.runAsync();
        assertThat(world.opened(ANN).editable())
                .as("the view-only window took no editor slot")
                .isTrue();
    }

    // ---- a window that never appears ---------------------------------------------------------------------------

    @Test
    void aWindowAnotherPluginRefusedFreesItsEditorSlot() {
        files.put(SAM, slots(DIAMONDS));
        world.showing = false;
        edits.openOffline(ANN, SAM, MirrorKind.INVENTORY, true);
        scheduler.runAsync();

        edits.notShown(world.opened(ANN));

        assertThat(edits.holding(ANN.uuid())).isFalse();
        assertThat(edits.openCount()).isZero();
        assertThat(openOffline(BEN, true).editable()).isTrue();
    }

    @Test
    void aWindowThatNeverAppearsIsGivenUpAndRefusedIfItTurnsUpLate() {
        files.put(SAM, slots(DIAMONDS));
        world.showing = false;
        edits.openOffline(ANN, SAM, MirrorKind.INVENTORY, true);
        scheduler.runAsync();
        MirrorEdits.Edit<Stack> ann = world.opened(ANN);
        assertThat(edits.opening(ann)).isTrue();

        scheduler.runLater();

        assertThat(edits.holding(ANN.uuid())).isFalse();
        assertThat(edits.opening(ann))
                .as("a window turning up now would move items nothing saves")
                .isFalse();
        assertThat(ann.accepting()).isFalse();
        assertThat(openOffline(BEN, true).editable()).isTrue();
    }

    @Test
    void aWindowThatAppearedIsKeptWhenTheWaitIsOver() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);

        scheduler.runLater();

        assertThat(edits.holding(ANN.uuid())).isTrue();
        assertThat(ann.accepting()).isTrue();
    }

    // ---- a save that throws ------------------------------------------------------------------------------------

    @Test
    void aSaveThatThrowsIsSettledAsFailedAndUndone() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        files.throwing = true;

        close(ann, MirrorEdits.Cause.CLOSED);
        scheduler.runAsync();

        assertThat(world.inventory(ANN)).isEmpty();
        assertThat(told).containsExactly("Ann: playerstate.mirror.undone-failed");
        assertThat(warnings).singleElement().asString().startsWith("event=mirror_edit_save_failed");
        assertThat(edits.holding(ANN.uuid())).isFalse();
        files.throwing = false;
        assertThat(openOffline(BEN, true).editable()).isTrue();
    }

    @Test
    void aLiveSaveThatThrowsMidwayCountsAsSavedForTheSlotsItWrote() {
        world.join(SAM, slots(DIAMONDS, GOLD));
        MirrorEdits.Edit<Stack> ann = openOnline(ANN, true);
        takeOut(ann, 0);
        takeOut(ann, 1);
        world.failingSlot = 1;

        close(ann, MirrorEdits.Cause.CLOSED);

        assertThat(world.live(SAM)).containsExactly(null, GOLD);
        assertThat(world.inventory(ANN)).containsExactly(DIAMONDS);
        assertThat(told).containsExactly("Ann: playerstate.mirror.partly-saved");
    }

    // ---- closes that cannot wait -------------------------------------------------------------------------------

    @Test
    void aDisconnectSavesAndTakesBackBeforeTheCloseReturns() {
        files.put(SAM, slots(DIAMONDS, null));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        files.slots(SAM).set(0, APPLE);

        close(ann, MirrorEdits.Cause.DISCONNECT);

        assertThat(scheduler.pendingAsync()).isZero();
        assertThat(world.inventory(ANN))
                .as("taken back before the server saves the viewer")
                .isEmpty();
    }

    @Test
    void stoppingSavesAndClosesEveryOpenWindowAtOnce() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);

        edits.stop();

        assertThat(world.closedByServer).containsExactly(ann);
        assertThat(scheduler.pendingAsync()).isZero();
        assertThat(files.slots(SAM)).containsExactly((Stack) null);
        assertThat(world.inventory(ANN)).containsExactly(DIAMONDS);
        assertThat(edits.openCount()).isZero();
    }

    @Test
    void stoppingFinishesASaveStillWaitingForTheAsyncLane() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);

        edits.stop();

        assertThat(files.slots(SAM)).containsExactly((Stack) null);
        assertThat(edits.openCount()).isZero();
        scheduler.runAsync();
        assertThat(files.saves)
                .as("the queued task does not save a second time")
                .isEqualTo(1);
    }

    @Test
    void aViewerWhoLeavesWithASaveQueuedFinishesItWhileStillHere() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);
        files.failing = true;

        edits.quitting(ANN);

        assertThat(world.inventory(ANN))
                .as("taken back before the server saves the viewer")
                .isEmpty();
        scheduler.runAsync();
        assertThat(files.saves).isEqualTo(1);
        assertThat(told).containsExactly("Ann: playerstate.mirror.undone-failed");
    }

    @Test
    void anOutcomeThatComesBackAfterTheViewerLeftIsLogged() {
        files.put(SAM, slots(DIAMONDS));
        MirrorEdits.Edit<Stack> ann = openOffline(ANN, true);
        takeOut(ann, 0);
        close(ann, MirrorEdits.Cause.CLOSED);
        files.failing = true;
        scheduler.leave(ANN);

        scheduler.runAsync();

        assertThat(warnings)
                .singleElement()
                .asString()
                .contains("event=offline_edit_not_saved", "viewer_holds_extra=[diamond x3]");
    }

    // ---- driving it --------------------------------------------------------------------------------------------

    private MirrorEdits.Edit<Stack> openOffline(PlayerRef viewer, boolean wantsEdit) {
        edits.openOffline(viewer, SAM, MirrorKind.INVENTORY, wantsEdit);
        scheduler.runAsync();
        return appeared(world.opened(viewer));
    }

    private MirrorEdits.Edit<Stack> openOnline(PlayerRef viewer, boolean wantsEdit) {
        edits.openOnline(viewer, SAM, MirrorKind.INVENTORY, wantsEdit);
        return appeared(world.opened(viewer));
    }

    /** The window appears, as the server reports it. */
    private MirrorEdits.Edit<Stack> appeared(MirrorEdits.Edit<Stack> edit) {
        if (world.windows.containsKey(edit)) {
            edits.shown(edit);
        }
        return edit;
    }

    /** The viewer takes a slot's items out of the window into their own inventory. */
    private void takeOut(MirrorEdits.Edit<Stack> edit, int slot) {
        Stack item = world.screen(edit).set(slot, null);
        if (item != null) {
            world.give(edit.viewer(), item);
        }
    }

    /** The viewer puts {@code item} from their own inventory into a slot of the window. */
    private void putIn(MirrorEdits.Edit<Stack> edit, int slot, @Nullable Stack item) {
        world.screen(edit).set(slot, item);
        if (item != null) {
            world.take(edit.viewer(), List.of(item));
        }
    }

    private void close(MirrorEdits.Edit<Stack> edit, MirrorEdits.Cause cause) {
        edits.closed(edit, world.windows.remove(edit), cause);
    }

    private static List<@Nullable Stack> slots(@Nullable Stack... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ---- fakes -------------------------------------------------------------------------------------------------

    record Stack(String id, int amount) {}

    private static final class StackRules implements MirrorEdits.Items<Stack> {

        @Override
        public boolean nothing(@Nullable Stack item) {
            return item == null || item.amount() <= 0;
        }

        @Override
        public boolean same(Stack a, Stack b) {
            return a.equals(b);
        }

        @Override
        public boolean similar(Stack a, Stack b) {
            return a.id().equals(b.id());
        }

        @Override
        public int amount(Stack item) {
            return item.amount();
        }

        @Override
        public Stack withAmount(Stack item, int amount) {
            return new Stack(item.id(), amount);
        }

        @Override
        public String describe(Stack item) {
            return item.id() + " x" + item.amount();
        }
    }

    /** The online players: live containers, staff inventories and the windows on their screens. */
    private static final class World implements MirrorEdits.Targets<Stack>, MirrorEdits.Viewers<Stack> {

        private final Set<UUID> online = new HashSet<>(Set.of(ANN.uuid(), BEN.uuid()));
        private final Map<UUID, List<@Nullable Stack>> live = new HashMap<>();
        private final Map<UUID, List<Stack>> inventories = new HashMap<>();
        private final Map<UUID, MirrorEdits.Edit<Stack>> opened = new HashMap<>();
        private final Map<MirrorEdits.Edit<Stack>, List<@Nullable Stack>> windows = new HashMap<>();
        private final List<MirrorEdits.Edit<Stack>> closedByServer = new ArrayList<>();
        private final Set<UUID> full = new HashSet<>();
        private boolean showing = true;
        private int failingSlot = -1;

        void join(PlayerRef player, List<@Nullable Stack> container) {
            online.add(player.uuid());
            live.put(player.uuid(), container);
        }

        void leave(PlayerRef player) {
            online.remove(player.uuid());
        }

        List<@Nullable Stack> live(PlayerRef player) {
            return Objects.requireNonNull(live.get(player.uuid()));
        }

        List<Stack> inventory(PlayerRef viewer) {
            return inventories.computeIfAbsent(viewer.uuid(), id -> new ArrayList<>());
        }

        MirrorEdits.Edit<Stack> opened(PlayerRef viewer) {
            return Objects.requireNonNull(opened.get(viewer.uuid()));
        }

        List<@Nullable Stack> screen(MirrorEdits.Edit<Stack> edit) {
            return Objects.requireNonNull(windows.get(edit));
        }

        @Override
        public boolean online(PlayerRef player) {
            return online.contains(player.uuid());
        }

        @Override
        public List<@Nullable Stack> contents(PlayerRef target, MirrorKind kind) {
            return new ArrayList<>(live(target));
        }

        @Override
        public @Nullable Stack slot(PlayerRef target, MirrorKind kind, int slot) {
            return live(target).get(slot);
        }

        @Override
        public void set(PlayerRef target, MirrorKind kind, int slot, @Nullable Stack item) {
            if (slot == failingSlot) {
                throw new IllegalStateException("the slot cannot be set");
            }
            live(target).set(slot, item);
        }

        @Override
        public List<Stack> take(PlayerRef viewer, List<Stack> items) {
            List<Stack> missing = new ArrayList<>();
            for (Stack item : items) {
                int left = item.amount();
                List<Stack> held = inventory(viewer);
                for (int i = 0; i < held.size() && left > 0; i++) {
                    Stack stack = held.get(i);
                    if (stack.id().equals(item.id())) {
                        int taken = Math.min(left, stack.amount());
                        left -= taken;
                        held.set(i, new Stack(stack.id(), stack.amount() - taken));
                    }
                }
                held.removeIf(stack -> stack.amount() == 0);
                if (left > 0) {
                    missing.add(new Stack(item.id(), left));
                }
            }
            return missing;
        }

        @Override
        public void give(PlayerRef viewer, List<Stack> items) {
            inventory(viewer).addAll(items);
        }

        void give(PlayerRef viewer, Stack item) {
            inventory(viewer).add(item);
        }

        @Override
        public boolean fits(PlayerRef viewer, Stack item) {
            return !full.contains(viewer.uuid());
        }

        @Override
        public void open(MirrorEdits.Edit<Stack> edit) {
            opened.put(edit.viewer().uuid(), edit);
            if (showing) {
                windows.put(edit, new ArrayList<>(edit.base()));
            }
        }

        @Override
        public Optional<List<@Nullable Stack>> window(MirrorEdits.Edit<Stack> edit) {
            return Optional.ofNullable(windows.get(edit));
        }

        @Override
        public void close(MirrorEdits.Edit<Stack> edit) {
            if (windows.remove(edit) != null) {
                closedByServer.add(edit);
            }
        }
    }

    /** The stored containers, checked slot by slot against what a window opened with, as the real file is. */
    private static final class Files implements MirrorEdits.Stored<Stack> {

        private final Map<UUID, List<@Nullable Stack>> stored = new HashMap<>();
        private boolean failing;
        private boolean throwing;
        private int saves;

        void put(PlayerRef player, List<@Nullable Stack> container) {
            stored.put(player.uuid(), container);
        }

        List<@Nullable Stack> slots(PlayerRef player) {
            return Objects.requireNonNull(stored.get(player.uuid()));
        }

        @Override
        public OfflineSnapshot<Stack> read(UUID player, MirrorKind kind) {
            return OfflineSnapshot.found(
                    Objects.requireNonNull(stored.get(player)).toArray(new Stack[0]),
                    new StoredSlots(1, Map.of()),
                    true);
        }

        @Override
        public SaveResult save(
                UUID player,
                MirrorKind kind,
                OfflineSnapshot<Stack> base,
                Map<Integer, @Nullable Stack> changes,
                Predicate<Set<Integer>> partly) {
            saves++;
            if (throwing) {
                throw new IllegalStateException("the disk is gone");
            }
            if (failing) {
                return SaveResult.of(SaveOutcome.FAILED);
            }
            List<@Nullable Stack> now = Objects.requireNonNull(stored.get(player));
            @Nullable Stack[] seen = base.items();
            Set<Integer> conflicts = new HashSet<>();
            changes.keySet().forEach(slot -> {
                if (!Objects.equals(now.get(slot), seen[slot])) {
                    conflicts.add(slot);
                }
            });
            if (!conflicts.isEmpty() && !partly.test(conflicts)) {
                return new SaveResult(SaveOutcome.CHANGED, conflicts);
            }
            changes.forEach((slot, item) -> {
                if (!conflicts.contains(slot)) {
                    now.set(slot, item);
                }
            });
            return new SaveResult(SaveOutcome.SAVED, conflicts);
        }
    }

    /**
     * Async work queues until {@link #runAsync}; hops to a player run at once, or take the retired branch for one who
     * has left. Like the main thread of a Paper server, every thread owns every entity.
     */
    private static final class ManualScheduler implements Scheduler {

        private final Deque<Runnable> async = new ArrayDeque<>();
        private final Deque<Runnable> later = new ArrayDeque<>();
        private final Set<UUID> gone = new HashSet<>();

        void runAsync() {
            Runnable next;
            while ((next = async.pollFirst()) != null) {
                next.run();
            }
        }

        /** Run only the async task queued last, ahead of those queued before it. */
        void runLastAsync() {
            Objects.requireNonNull(async.pollLast()).run();
        }

        /** Run what was put off for later, as if the delay has passed. */
        void runLater() {
            Runnable next;
            while ((next = later.pollFirst()) != null) {
                next.run();
            }
        }

        int pendingAsync() {
            return async.size();
        }

        void leave(PlayerRef who) {
            gone.add(who.uuid());
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            if (!gone.contains(player.uuid())) {
                task.run();
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (gone.contains(player.uuid())) {
                retired.run();
            } else {
                task.run();
            }
        }

        @Override
        public boolean ownsEntity(PlayerRef player) {
            return true;
        }

        @Override
        public void laterEntity(PlayerRef player, java.time.Duration delay, Runnable task) {
            later.add(() -> onEntity(player, task));
        }

        @Override
        public void async(Runnable task) {
            async.add(task);
        }

        @Override
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            async.add(task);
        }
    }

    private record Warnings(List<String> lines) implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            lines.add(line);
        }

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
