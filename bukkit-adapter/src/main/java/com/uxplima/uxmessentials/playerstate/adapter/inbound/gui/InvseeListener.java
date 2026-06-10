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
 * Routes the click, drag, and close events for {@code /invsee} menus, recognised by their {@link InvseeHolder}.
 * The policy keeps the menu dupe-safe: a view-only viewer has every interaction with the menu cancelled, and an
 * editing viewer may only touch the editable region ({@link InvseeLayout#isEditable}). Clicks on the filler pane,
 * shift-clicks and hotbar swaps that could shove an item into the menu, and drags that touch a non-editable slot
 * are all cancelled. The actual inventory mutation happens only in the viewer's private menu copy; on close
 * {@link InvseeView#onClose} reconciles that copy back onto the target in one pass.
 */
@NullMarked
public final class InvseeListener implements Listener {

    private final InvseeView view;

    public InvseeListener(InvseeView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InvseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (!holder.editable() || !ManagedMenuPolicy.clickAllowed(event, InvseeLayout::isEditable)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InvseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (!holder.editable() || ManagedMenuPolicy.dragTouchesNonEditable(event, InvseeLayout::isEditable)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InvseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null) {
            view.onClose(holder);
        }
    }

    private static @Nullable InvseeHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof InvseeHolder invsee ? invsee : null;
    }
}
