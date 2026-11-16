package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens {@code /invsee} and {@code /endersee} against an <em>offline</em> target. The target's stored items are read
 * through {@link OfflinePlayerStorage} on the async lane, then the same managed menu the online {@link InvseeView}
 * and {@link EnderseeView} use is opened on the viewer's entity thread, seeded from that snapshot. The viewer edits
 * the copy; on close the edit is saved.
 *
 * <p>A save is a difference, not a copy: only the slots the viewer changed are written, and only if each still holds
 * what the window was opened with. Writing the whole window back would undo anything that changed the container in
 * between, and since the viewer may have taken items out of the window into their own inventory, that would hand
 * the same items out twice. If the target is online again by the close, the difference is applied to their live
 * container on their own thread under the same rule; otherwise it goes to their data file.
 *
 * <p>One edit per container at a time: a second staff member opening a container someone is editing gets it view
 * only, so two people never take the same items. An open waits briefly for the same container's save still under
 * way, so closing and reopening shows the saved state. A save that is refused or fails reopens the window with the
 * viewer's changes still in it and says why; the edit stays theirs until it is saved or put back, so nothing is
 * lost or doubled because a save did not go through.
 */
@NullMarked
public final class OfflineContainerView {

    private static final String MODIFY_PERMISSION = PlayerstatePermissions.INVSEE_MODIFY;

    /** How long an open waits for the same container's save, still under way, before it reads anyway. */
    private static final Duration SAVE_WAIT = Duration.ofSeconds(3);

    private final Scheduler scheduler;
    private final OfflinePlayerStorage storage;
    private final MirrorWindow window;
    private final Notifier notifier;
    private final Logger log;
    private final Set<MirrorHolder> open = ConcurrentHashMap.newKeySet();
    private final Map<MirrorHolder, Edit> edits = new ConcurrentHashMap<>();
    private final Map<Container, Edit> editing = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    public OfflineContainerView(
            Scheduler scheduler, OfflinePlayerStorage storage, MirrorWindow window, Notifier notifier, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.window = Objects.requireNonNull(window, "window");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** One player's inventory or ender chest. */
    private record Container(UUID player, MirrorKind kind) {}

    /**
     * One staff member's edit of one container, from the open until it is saved or given up, across every window
     * reopened for it. The base is what the edit started from, and stays so: a reopened window still saves against it.
     */
    private static final class Edit {

        private final Container container;
        private final PlayerRef editor;
        private final OfflineSnapshot base;
        private final CompletableFuture<Void> settled = new CompletableFuture<>();
        private volatile boolean saving;

        Edit(Container container, PlayerRef editor, OfflineSnapshot base) {
            this.container = container;
            this.editor = editor;
            this.base = base;
        }
    }

    /** Open {@code subject}'s stored inventory for {@code viewer}. */
    public void openInventory(PlayerRef viewer, PlayerRef subject) {
        open(viewer, subject, MirrorKind.INVENTORY);
    }

    /** Open {@code subject}'s stored ender chest for {@code viewer}. */
    public void openEnderChest(PlayerRef viewer, PlayerRef subject) {
        open(viewer, subject, MirrorKind.ENDER);
    }

    /**
     * Save every still-open offline menu; called on module stop. A save that does not go through can no longer
     * reopen its window, so it is reported and logged instead.
     */
    public void flushAll() {
        stopping = true;
        for (MirrorHolder holder : Set.copyOf(open)) {
            window.drain(holder);
        }
    }

    /** The number of offline storage menus currently open. */
    public int openCount() {
        return open.size();
    }

    private void open(PlayerRef viewer, PlayerRef subject, MirrorKind kind) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        Container container = new Container(subject.uuid(), kind);
        scheduler.async(() -> {
            awaitSave(container);
            OfflineSnapshot snapshot = storage.read(subject.uuid(), layoutOf(kind));
            scheduler.onEntity(viewer, () -> show(viewer, subject, container, snapshot));
        });
    }

    /** Wait, on the async lane, for a save of {@code container} that is under way, so the read sees it. */
    private void awaitSave(Container container) {
        Edit current = editing.get(container);
        if (current == null || !current.saving) {
            return;
        }
        try {
            current.settled.get(SAVE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException slow) {
            // Read anyway: the open then finds the container being edited and opens it view only.
        }
    }

    private void show(PlayerRef viewer, PlayerRef subject, Container container, OfflineSnapshot snapshot) {
        Player looker = LivePlayers.of(viewer).orElse(null);
        if (looker == null) {
            return;
        }
        SaveOutcome problem = snapshot.problem().orElse(null);
        if (problem != null) {
            notifier.send(viewer, readProblem(problem), Map.of("player", subject.name()));
            return;
        }
        boolean wantsEdit = container.kind() == MirrorKind.ENDER || looker.hasPermission(MODIFY_PERMISSION);
        Edit edit = null;
        if (wantsEdit && !snapshot.editable()) {
            notifier.send(viewer, PlayerstateMessageKey.OFFLINE_VIEW_ONLY_OLD, Map.of("player", subject.name()));
        } else if (wantsEdit) {
            Edit candidate = new Edit(container, viewer, snapshot);
            Edit held = editing.putIfAbsent(container, candidate);
            if (held == null) {
                edit = candidate;
            } else {
                notifier.send(
                        viewer, PlayerstateMessageKey.OFFLINE_VIEW_ONLY_EDITED, Map.of("editor", held.editor.name()));
            }
        }
        openWindow(viewer, subject, container.kind(), snapshot.items(), edit);
    }

    private void openWindow(
            PlayerRef viewer, PlayerRef subject, MirrorKind kind, @Nullable ItemStack[] items, @Nullable Edit edit) {
        MirrorHolder holder = new MirrorHolder(viewer, subject, edit != null, kind, items, this::reconcile);
        open.add(holder);
        if (edit != null) {
            edits.put(holder, edit);
        }
        window.open(holder);
    }

    /**
     * Claim the window and save what changed in it. A view-only window, or one whose contents are what it opened
     * with, changes nothing and writes nothing.
     */
    private void reconcile(MirrorHolder holder, @Nullable ItemStack[] contents) {
        if (!open.remove(holder)) {
            return;
        }
        Edit edit = edits.remove(holder);
        if (edit == null) {
            return;
        }
        if (edit.base.changedSlots(contents).isEmpty()) {
            settle(edit);
            return;
        }
        edit.saving = true;
        PlayerRef subject = holder.target();
        if (LivePlayers.of(subject).isPresent()) {
            scheduler.onEntity(
                    subject,
                    () -> afterSave(holder, edit, contents, applyLive(edit, contents, subject)),
                    () -> afterSave(holder, edit, contents, SaveOutcome.PLAYER_PRESENT));
        } else {
            scheduler.async(() -> afterSave(
                    holder,
                    edit,
                    contents,
                    storage.save(subject.uuid(), layoutOf(holder.kind()), edit.base, contents)));
        }
    }

    /**
     * Apply the edit to the online target's live container, on their own thread: every changed slot must still hold
     * what the window opened with, or nothing is changed.
     */
    private SaveOutcome applyLive(Edit edit, @Nullable ItemStack[] contents, PlayerRef subject) {
        Player target = LivePlayers.of(subject).orElse(null);
        if (target == null) {
            return SaveOutcome.PLAYER_PRESENT;
        }
        MirrorKind kind = edit.container.kind();
        List<Integer> changed = edit.base.changedSlots(contents);
        for (int slot : changed) {
            if (!OfflineSnapshot.sameItem(liveSlot(kind, target, slot), edit.base.item(slot))) {
                return SaveOutcome.CHANGED;
            }
        }
        for (int slot : changed) {
            ItemStack item = slot < contents.length ? contents[slot] : null;
            if (kind == MirrorKind.INVENTORY) {
                InvseeLayout.setLiveSlot(target, slot, item);
            } else {
                EnderLayout.setLiveSlot(target, slot, item);
            }
        }
        return SaveOutcome.SAVED;
    }

    private void afterSave(MirrorHolder holder, Edit edit, @Nullable ItemStack[] contents, SaveOutcome outcome) {
        if (outcome == SaveOutcome.SAVED) {
            settle(edit);
            return;
        }
        PlayerRef viewer = holder.viewer();
        Map<String, String> target = Map.of("player", holder.target().name());
        if (stopping) {
            notifier.send(viewer, PlayerstateMessageKey.OFFLINE_NOT_SAVED_CLOSED, target);
            lost(holder, edit, contents, outcome);
            return;
        }
        scheduler.onEntity(
                viewer,
                () -> {
                    if (LivePlayers.of(viewer).isEmpty()) {
                        lost(holder, edit, contents, outcome);
                        return;
                    }
                    edit.saving = false;
                    openWindow(viewer, holder.target(), holder.kind(), contents, edit);
                    notifier.send(viewer, notSaved(outcome), target);
                },
                () -> lost(holder, edit, contents, outcome));
    }

    /** The edit can no longer be handed back to its viewer; the log keeps what it held so staff can restore it. */
    private void lost(MirrorHolder holder, Edit edit, @Nullable ItemStack[] contents, SaveOutcome outcome) {
        String changed = edit.base.changedSlots(contents).stream()
                .map(slot -> slot + "=" + describe(slot < contents.length ? contents[slot] : null))
                .collect(Collectors.joining(", "));
        log.warn(
                "event=offline_edit_not_saved viewer={} target={} container={} outcome={} changed_slots=[{}]",
                holder.viewer().name(),
                holder.target().name(),
                holder.kind().name().toLowerCase(Locale.ROOT),
                outcome.name(),
                changed);
        settle(edit);
    }

    private void settle(Edit edit) {
        editing.remove(edit.container, edit);
        edit.settled.complete(null);
    }

    private static @Nullable ItemStack liveSlot(MirrorKind kind, Player target, int slot) {
        return kind == MirrorKind.INVENTORY ? InvseeLayout.liveSlot(target, slot) : EnderLayout.liveSlot(target, slot);
    }

    private static String describe(@Nullable ItemStack item) {
        return item == null || OfflineSnapshot.nothing(item) ? "empty" : item.getAmount() + "x" + item.getType();
    }

    private static ContainerLayout layoutOf(MirrorKind kind) {
        return kind == MirrorKind.INVENTORY ? ContainerLayout.INVENTORY : ContainerLayout.ENDER;
    }

    private static PlayerstateMessageKey readProblem(SaveOutcome problem) {
        return switch (problem) {
            case PLAYER_PRESENT -> PlayerstateMessageKey.OFFLINE_JOINING;
            case NO_DATA -> PlayerstateMessageKey.OFFLINE_NO_DATA;
            case UNAVAILABLE -> PlayerstateMessageKey.OFFLINE_UNAVAILABLE;
            case SAVED, CHANGED, FAILED -> PlayerstateMessageKey.OFFLINE_READ_FAILED;
        };
    }

    private static PlayerstateMessageKey notSaved(SaveOutcome outcome) {
        return switch (outcome) {
            case PLAYER_PRESENT -> PlayerstateMessageKey.OFFLINE_NOT_SAVED_JOINING;
            case CHANGED -> PlayerstateMessageKey.OFFLINE_NOT_SAVED_CHANGED;
            case SAVED, NO_DATA, FAILED, UNAVAILABLE -> PlayerstateMessageKey.OFFLINE_NOT_SAVED_FAILED;
        };
    }
}
