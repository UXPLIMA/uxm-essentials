package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /invrestore} snapshot list: a paginated, engine-backed panel (the shared {@link EntityListView}, so
 * it renders through the menu engine and needs no raw-inventory allow-list entry) of one icon per snapshot the
 * target holds, newest first. The target's snapshots are resolved off the tick thread through the
 * {@link SnapshotRepository} (whose {@code list} is ordered newest-first), so the DB read never blocks a region
 * thread; the panel is then shown on the staff member's own entity thread. Each icon's material tracks the capture
 * cause and its name/lore label the cause, timestamp and the click-to-preview hint. Selecting an icon opens the
 * read-only {@link SnapshotPreviewView} for that snapshot, from which the restore is confirmed.
 *
 * <p>A fresh {@link EntityListView} is built per open over the resolved snapshot list, so two staff members
 * inspecting different targets never share list state.
 */
@NullMarked
public final class SnapshotListView {

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final SnapshotRepository repository;
    private final SnapshotPreviewView preview;

    public SnapshotListView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            SnapshotRepository repository,
            SnapshotPreviewView preview) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.preview = Objects.requireNonNull(preview, "preview");
    }

    /**
     * Resolve {@code target}'s snapshots off-thread, then open the newest-first list for {@code staff} — or, when
     * the target holds none, send {@code staff} the "no snapshots" line instead of an empty window.
     */
    public void open(PlayerRef staff, PlayerRef target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        scheduler.async(() -> {
            List<Snapshot> snapshots = repository.list(target.uuid());
            if (snapshots.isEmpty()) {
                messageSink.deliver(
                        staff,
                        messages.resolve(
                                staff,
                                InvrollbackMessageKey.INVROLLBACK_NO_SNAPSHOTS,
                                Map.of("player", target.name())));
                return;
            }
            scheduler.onEntity(staff, () -> openResolved(staff, target, snapshots));
        });
    }

    private void openResolved(PlayerRef staff, PlayerRef target, List<Snapshot> snapshots) {
        Player viewer = Bukkit.getPlayer(staff.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        EntityListView.<Snapshot>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(EntityListLayout.paginatedDefault(Material.CHEST))
                .title(InvrollbackMessageKey.INVROLLBACK_GUI_TITLE)
                .navNames(InvrollbackMessageKey.INVROLLBACK_GUI_PREV, InvrollbackMessageKey.INVROLLBACK_GUI_NEXT)
                .entities(() -> snapshots)
                .iconRenderer((who, snapshot) -> icon(who, target, snapshot))
                .onSelect((clicker, snapshot) -> preview.open(BukkitRefs.toRef(clicker), target, snapshot))
                .build()
                .open(viewer, staff);
    }

    private ItemStack icon(PlayerRef viewer, PlayerRef target, Snapshot snapshot) {
        Map<String, String> placeholders = SnapshotDisplay.placeholders(target, snapshot);
        return ItemBuilder.of(iconMaterial(snapshot.cause()))
                .name(guiText.text(viewer, InvrollbackMessageKey.INVROLLBACK_GUI_SNAPSHOT, placeholders))
                .lore(guiText.text(viewer, InvrollbackMessageKey.INVROLLBACK_GUI_VIEW, placeholders))
                .build();
    }

    private static Material iconMaterial(SnapshotCause cause) {
        return switch (cause) {
            case DEATH -> Material.SKELETON_SKULL;
            case LOGOUT -> Material.ENDER_PEARL;
            case RESTORE -> Material.CLOCK;
        };
    }
}
