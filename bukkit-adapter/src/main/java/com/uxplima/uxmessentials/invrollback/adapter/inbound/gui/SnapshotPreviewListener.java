package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a snapshot-preview window strictly read-only and routes its one live control. Recognising a preview by its
 * {@link SnapshotPreviewHolder}, it cancels every click and drag so no item can be taken from or shuffled into the
 * preview (a shift-click from the staff member's own inventory included); a click on the restore-button slot in the
 * preview itself additionally hands off to {@link SnapshotPreviewView#onRestoreClick}. No close handling is needed —
 * a preview mirrors a stored snapshot and reconciles nothing back.
 */
@NullMarked
public final class SnapshotPreviewListener implements Listener {

    private final SnapshotPreviewView view;

    public SnapshotPreviewListener(SnapshotPreviewView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        SnapshotPreviewHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        boolean clickedPreview = event.getClickedInventory() == event.getView().getTopInventory();
        if (!clickedPreview || !(event.getWhoClicked() instanceof Player who)) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.isRestoreSlot(slot)) {
            view.onRestoreClick(holder, who);
        } else if (holder.isTeleportSlot(slot)) {
            view.onTeleportClick(holder, who);
        } else if (holder.isExportSlot(slot)) {
            view.onExportClick(holder, who);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    private static @Nullable SnapshotPreviewHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof SnapshotPreviewHolder preview ? preview : null;
    }
}
