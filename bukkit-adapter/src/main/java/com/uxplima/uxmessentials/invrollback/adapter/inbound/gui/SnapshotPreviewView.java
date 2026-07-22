package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec.Summary;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens a chosen snapshot's contents as a read-only preview window with a control row: a details panel, a teleport
 * button, a restore button, and an export-to-shulkers button. It is a sanctioned raw-inventory leaf (not a spec
 * menu: it mirrors decoded item bytes into a fixed 54-slot frame the way the playerstate {@code InvseeView} mirrors
 * a live inventory, and it is on the ArchUnit createInventory allow-list). The decoded main inventory (hotbar,
 * storage, armour, offhand) is copied into the top five rows; the bottom row is a filler background carrying the
 * four controls. Every click is cancelled by {@link SnapshotPreviewListener} so the preview is genuinely read-only;
 * a click on a control routes to the matching handler, which hands off to the {@link SnapshotRestorer},
 * {@link SnapshotTeleporter}, or {@link SnapshotExporter}.
 *
 * <p>The window is built and shown on the staff member's own entity thread, where touching their live screen is
 * legal. No edit is ever reconciled back: the preview shows a stored snapshot, and only the explicit controls
 * mutate anything, so there is nothing to track or flush on close.
 */
@NullMarked
public final class SnapshotPreviewView {

    static final int SIZE = 54;

    /** The details panel sits on the left of the bottom row; the five rows above show the snapshot's items. */
    static final int INFO_SLOT = 45;

    /** The teleport-to-scene button. */
    static final int TELEPORT_SLOT = 47;

    /** The restore button sits in the middle of the bottom row. */
    static final int RESTORE_SLOT = 49;

    /** The export-to-shulkers button. */
    static final int EXPORT_SLOT = 51;

    /** Slots 0..44 (the top five rows) mirror the decoded inventory; the bottom row is filler plus the controls. */
    private static final int CONTENT_SLOTS = 45;

    private final Messages messages;
    private final Scheduler scheduler;
    private final Clock clock;
    private final SnapshotRestorer restorer;
    private final SnapshotTeleporter teleporter;
    private final SnapshotExporter exporter;

    public SnapshotPreviewView(
            Messages messages,
            Scheduler scheduler,
            Clock clock,
            SnapshotRestorer restorer,
            SnapshotTeleporter teleporter,
            SnapshotExporter exporter) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.restorer = Objects.requireNonNull(restorer, "restorer");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    /** Open a read-only preview of {@code snapshot} (owned by {@code target}) for {@code staff}. */
    public void open(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        scheduler.onEntity(staff, () -> openResolved(staff, target, snapshot));
    }

    private void openResolved(PlayerRef staff, PlayerRef target, Snapshot snapshot) {
        Player viewer = Bukkit.getPlayer(staff.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        Summary summary = InventorySnapshotCodec.summarize(snapshot.contents());
        Map<String, String> base = SnapshotDisplay.base(target, snapshot, summary, clock.instant());
        SnapshotPreviewHolder holder =
                new SnapshotPreviewHolder(target, snapshot, RESTORE_SLOT, TELEPORT_SLOT, EXPORT_SLOT);
        Component title =
                StyledText.render(messages.resolve(staff, InvrollbackMessageKey.INVROLLBACK_GUI_PREVIEW_TITLE, base));
        Inventory window = Bukkit.createInventory(holder, SIZE, title);
        holder.attach(window);
        seed(window, snapshot, summary, staff, base);
        viewer.openInventory(window);
    }

    /** Route a restore-button click: close the preview and hand the restore to the {@link SnapshotRestorer}. */
    void onRestoreClick(SnapshotPreviewHolder holder, Player viewer) {
        viewer.closeInventory();
        restorer.restore(BukkitRefs.toRef(viewer), holder.target(), holder.snapshotId());
    }

    /** Route a teleport-button click: close the preview and hand the hop to the {@link SnapshotTeleporter}. */
    void onTeleportClick(SnapshotPreviewHolder holder, Player viewer) {
        viewer.closeInventory();
        teleporter.teleport(BukkitRefs.toRef(viewer), holder.target(), holder.snapshot());
    }

    /** Route an export-button click: close the preview and hand the packaging to the {@link SnapshotExporter}. */
    void onExportClick(SnapshotPreviewHolder holder, Player viewer) {
        viewer.closeInventory();
        exporter.export(BukkitRefs.toRef(viewer), holder.target(), holder.snapshot());
    }

    private void seed(Inventory window, Snapshot snapshot, Summary summary, PlayerRef staff, Map<String, String> base) {
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        @Nullable ItemStack[] contents = decoded.contents();
        ItemStack filler = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot == INFO_SLOT) {
                window.setItem(slot, infoPanel(staff, summary, base));
            } else if (slot == TELEPORT_SLOT) {
                window.setItem(slot, teleportButton(staff));
            } else if (slot == RESTORE_SLOT) {
                window.setItem(slot, restoreButton(staff));
            } else if (slot == EXPORT_SLOT) {
                window.setItem(slot, exportButton(staff));
            } else if (slot < CONTENT_SLOTS && slot < contents.length && contents[slot] != null) {
                window.setItem(slot, contents[slot].clone());
            } else {
                window.setItem(slot, filler.clone());
            }
        }
    }

    private ItemStack infoPanel(PlayerRef staff, Summary summary, Map<String, String> base) {
        List<Component> lore = new ArrayList<>();
        lore.add(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_INFO, base));
        summary.location()
                .ifPresent(position -> lore.add(text(
                        staff, InvrollbackMessageKey.INVROLLBACK_GUI_LOCATION, SnapshotDisplay.location(position))));
        return ItemBuilder.of(Material.BOOK)
                .name(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_INFO_NAME))
                .lore(lore)
                .build();
    }

    private ItemStack teleportButton(PlayerRef staff) {
        return ItemBuilder.of(Material.ENDER_PEARL)
                .name(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_TELEPORT_NAME))
                .lore(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_TELEPORT))
                .build();
    }

    private ItemStack restoreButton(PlayerRef staff) {
        return ItemBuilder.of(Material.LIME_WOOL)
                .name(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_RESTORE_NAME))
                .lore(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_RESTORE))
                .build();
    }

    private ItemStack exportButton(PlayerRef staff) {
        return ItemBuilder.of(Material.SHULKER_BOX)
                .name(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_EXPORT_NAME))
                .lore(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_EXPORT))
                .build();
    }

    private static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
    }

    private Component text(PlayerRef staff, MessageKey key) {
        return StyledText.render(messages.resolve(staff, key, Map.of()));
    }

    private Component text(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(staff, key, placeholders));
    }
}
