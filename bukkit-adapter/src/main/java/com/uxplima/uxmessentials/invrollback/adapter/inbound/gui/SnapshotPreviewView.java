package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
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
 * Opens a chosen snapshot's contents as a read-only preview window with a single restore button — a sanctioned
 * raw-inventory leaf (it is not a spec menu: it mirrors decoded item bytes into a fixed 54-slot frame the way the
 * playerstate {@code InvseeView} mirrors a live inventory, and it is on the ArchUnit createInventory allow-list).
 * The decoded main inventory (hotbar, storage, armour, offhand) is copied into the top five rows; the bottom row is
 * a filler background with a lime restore button. Every click is cancelled by {@link SnapshotPreviewListener} so the
 * preview is genuinely read-only; a click on the restore button routes to {@link #onRestoreClick}, which closes the
 * window and hands off to the {@link SnapshotRestorer} (which safety-snapshots the pre-restore state first).
 *
 * <p>The window is built and shown on the staff member's own entity thread, where touching their live screen is
 * legal. No edit is ever reconciled back — the preview shows a stored snapshot, and only the explicit restore
 * button mutates anything — so there is nothing to track or flush on close.
 */
@NullMarked
public final class SnapshotPreviewView {

    static final int SIZE = 54;

    /** The restore button sits in the middle of the bottom row; the five rows above show the snapshot's items. */
    static final int RESTORE_SLOT = 49;

    /** Slots 0..44 (the top five rows) mirror the decoded inventory; the bottom row is filler plus the button. */
    private static final int CONTENT_SLOTS = 45;

    private final Messages messages;
    private final Scheduler scheduler;
    private final SnapshotRestorer restorer;

    public SnapshotPreviewView(Messages messages, Scheduler scheduler, SnapshotRestorer restorer) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.restorer = Objects.requireNonNull(restorer, "restorer");
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
        SnapshotPreviewHolder holder = new SnapshotPreviewHolder(target, snapshot.id(), RESTORE_SLOT);
        Component title = text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_PREVIEW_TITLE, target, snapshot);
        Inventory window = Bukkit.createInventory(holder, SIZE, title);
        holder.attach(window);
        seed(window, snapshot, staff);
        viewer.openInventory(window);
    }

    /** Route a restore-button click: close the preview and hand the restore to the {@link SnapshotRestorer}. */
    void onRestoreClick(SnapshotPreviewHolder holder, Player viewer) {
        viewer.closeInventory();
        restorer.restore(BukkitRefs.toRef(viewer), holder.target(), holder.snapshotId());
    }

    private void seed(Inventory window, Snapshot snapshot, PlayerRef staff) {
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        @Nullable ItemStack[] contents = decoded.contents();
        ItemStack filler = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot == RESTORE_SLOT) {
                window.setItem(slot, restoreButton(staff));
            } else if (slot < CONTENT_SLOTS && slot < contents.length && contents[slot] != null) {
                window.setItem(slot, contents[slot].clone());
            } else {
                window.setItem(slot, filler.clone());
            }
        }
    }

    private ItemStack restoreButton(PlayerRef staff) {
        return ItemBuilder.of(Material.LIME_WOOL)
                .name(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_RESTORE_NAME))
                .lore(text(staff, InvrollbackMessageKey.INVROLLBACK_GUI_RESTORE))
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

    private Component text(PlayerRef staff, MessageKey key, PlayerRef target, Snapshot snapshot) {
        return StyledText.render(messages.resolve(staff, key, SnapshotDisplay.placeholders(target, snapshot)));
    }
}
