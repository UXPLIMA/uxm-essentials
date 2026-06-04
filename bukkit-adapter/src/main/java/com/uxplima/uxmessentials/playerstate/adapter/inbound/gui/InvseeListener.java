package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
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
        if (!holder.editable() || !clickAllowed(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InvseeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesNonEditable =
                event.getRawSlots().stream().anyMatch(raw -> raw < topSize && !InvseeLayout.isEditable(raw));
        if (!holder.editable() || touchesNonEditable) {
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

    /** Whether an editing viewer's click is safe: it must not move an item into a non-editable menu slot. */
    private static boolean clickAllowed(InventoryClickEvent event) {
        if (unboundedIntoTop(event.getAction())) {
            // A shift-click or double-click collect can land an item anywhere in the top with no named
            // destination, so it could park on the filler pane; deny it outright.
            return false;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return true; // a click in the viewer's own inventory is theirs to make
        }
        // A click that lands on a specific top slot (including a hotbar swap onto it) is fine when editable.
        return InvseeLayout.isEditable(event.getSlot());
    }

    /** Actions that can push an item into the top inventory without naming a destination slot. */
    private static boolean unboundedIntoTop(InventoryAction action) {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    private static @Nullable InvseeHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof InvseeHolder invsee ? invsee : null;
    }
}
