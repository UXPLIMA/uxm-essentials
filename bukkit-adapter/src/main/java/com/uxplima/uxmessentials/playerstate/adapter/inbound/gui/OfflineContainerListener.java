package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Routes click, drag, and close events for offline {@code /invsee} and {@code /endersee} menus, recognised by
 * their {@link OfflineHolder}. The dupe-safe policy is the shared {@link ManagedMenuPolicy}, parameterised by the
 * holder's own editable-slot shape (the 54-slot invsee layout, or all 27 ender slots). On close the
 * {@link OfflineContainerView} reconciles the edited copy back onto the offline player's stored data — or onto
 * their live inventory if they have logged in while the menu was open.
 */
@NullMarked
public final class OfflineContainerListener implements Listener {

    private final OfflineContainerView view;

    public OfflineContainerListener(OfflineContainerView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        OfflineHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (!holder.editable() || !ManagedMenuPolicy.clickAllowed(event, holder::isEditableSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        OfflineHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (!holder.editable() || ManagedMenuPolicy.dragTouchesNonEditable(event, holder::isEditableSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        OfflineHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null) {
            view.onClose(holder);
        }
    }

    private static @Nullable OfflineHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof OfflineHolder offline ? offline : null;
    }
}
