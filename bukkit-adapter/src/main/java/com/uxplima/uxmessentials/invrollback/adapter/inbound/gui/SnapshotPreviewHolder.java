package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a snapshot-preview window so {@link SnapshotPreviewListener} can recognise
 * a click or close as belonging to one of these read-only views (and never to a vanilla container the staff member
 * happens to have open), read which target and which snapshot it previews, and route a click on the restore-button
 * slot. The holder is created first and the window is built against it; {@link #attach} then stores the built
 * inventory so {@link #getInventory()} can answer it, the way Bukkit's holder contract expects.
 *
 * @see SnapshotPreviewView
 */
@NullMarked
final class SnapshotPreviewHolder implements InventoryHolder {

    private final PlayerRef target;
    private final SnapshotId snapshotId;
    private final int restoreSlot;
    private @Nullable Inventory inventory;

    SnapshotPreviewHolder(PlayerRef target, SnapshotId snapshotId, int restoreSlot) {
        this.target = Objects.requireNonNull(target, "target");
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.restoreSlot = restoreSlot;
    }

    /** The player whose snapshot this window previews and whose inventory a restore overwrites. */
    PlayerRef target() {
        return target;
    }

    /** The snapshot the restore button applies. */
    SnapshotId snapshotId() {
        return snapshotId;
    }

    /** Whether {@code rawSlot} is the restore button. */
    boolean isRestoreSlot(int rawSlot) {
        return rawSlot == restoreSlot;
    }

    /** Store the built window so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("snapshot preview inventory not attached yet");
        }
        return built;
    }
}
