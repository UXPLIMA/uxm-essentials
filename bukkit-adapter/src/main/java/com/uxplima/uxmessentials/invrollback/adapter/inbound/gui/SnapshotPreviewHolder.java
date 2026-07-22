package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a snapshot-preview window so {@link SnapshotPreviewListener} can recognise
 * a click or close as belonging to one of these read-only views (and never to a vanilla container the staff member
 * happens to have open), read which target and which snapshot it previews, and route a click on one of the three
 * control-button slots (restore, teleport, export). The holder carries the whole {@link Snapshot} so the teleport
 * and export actions can read its location and items without a second database round-trip. The holder is created
 * first and the window is built against it; {@link #attach} then stores the built inventory so
 * {@link #getInventory()} can answer it, the way Bukkit's holder contract expects.
 *
 * @see SnapshotPreviewView
 */
@NullMarked
final class SnapshotPreviewHolder implements InventoryHolder {

    private final PlayerRef target;
    private final Snapshot snapshot;
    private final int restoreSlot;
    private final int teleportSlot;
    private final int exportSlot;
    private @Nullable Inventory inventory;

    SnapshotPreviewHolder(PlayerRef target, Snapshot snapshot, int restoreSlot, int teleportSlot, int exportSlot) {
        this.target = Objects.requireNonNull(target, "target");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.restoreSlot = restoreSlot;
        this.teleportSlot = teleportSlot;
        this.exportSlot = exportSlot;
    }

    /** The player whose snapshot this window previews and whose inventory a restore overwrites. */
    PlayerRef target() {
        return target;
    }

    /** The previewed snapshot, carried whole so the teleport/export actions read its payload directly. */
    Snapshot snapshot() {
        return snapshot;
    }

    /** The snapshot the restore button applies. */
    SnapshotId snapshotId() {
        return snapshot.id();
    }

    /** Whether {@code rawSlot} is the restore button. */
    boolean isRestoreSlot(int rawSlot) {
        return rawSlot == restoreSlot;
    }

    /** Whether {@code rawSlot} is the teleport button. */
    boolean isTeleportSlot(int rawSlot) {
        return rawSlot == teleportSlot;
    }

    /** Whether {@code rawSlot} is the export button. */
    boolean isExportSlot(int rawSlot) {
        return rawSlot == exportSlot;
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
