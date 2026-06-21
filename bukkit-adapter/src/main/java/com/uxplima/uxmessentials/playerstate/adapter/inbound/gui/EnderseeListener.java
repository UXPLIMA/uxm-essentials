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
 * Routes the click, drag, and close events for online {@code /endersee} menus, recognised by their {@link
 * EnderseeHolder}. The shared {@link ManagedMenuPolicy} keeps the menu dupe-safe: a shift-click or collect that
 * could shove an item into a slot the menu does not mirror back is cancelled. The actual mutation happens only in
 * the viewer's private menu copy; on close {@link EnderseeView#onClose} reconciles that copy back onto the target's
 * live ender chest in one pass. Mirrors {@link InvseeListener}, but every ender slot is editable.
 */
@NullMarked
public final class EnderseeListener implements Listener {

    private final EnderseeView view;

    public EnderseeListener(EnderseeView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        EnderseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (!ManagedMenuPolicy.clickAllowed(event, EnderLayout::isEditable)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        EnderseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (ManagedMenuPolicy.dragTouchesNonEditable(event, EnderLayout::isEditable)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        EnderseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null) {
            view.onClose(holder);
        }
    }

    private static @Nullable EnderseeHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof EnderseeHolder endersee ? endersee : null;
    }
}
