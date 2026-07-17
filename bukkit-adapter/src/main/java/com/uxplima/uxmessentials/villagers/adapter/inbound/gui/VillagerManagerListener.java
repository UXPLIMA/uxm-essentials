package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;

/**
 * Routes the raw inventory events of a {@code /villager manager} window to its {@link VillagerManagerView}. A click on
 * the window is handed to the view, which decides whether to cancel it (the disable toggle, the remove buttons, and
 * the static filler are cancelled; only a buy/sell slot lets an item move); a click in the editor's own inventory is
 * left alone unless it is a shift-move that would jump a stack into a non-editable window slot. A drag that touches any
 * non-editable window slot is cancelled so a split cannot smear items across the controls. Closing the window applies
 * the edits through the view. Windows are recognised by their {@link VillagerManagerHolder}, so a vanilla container the
 * editor also has open is never touched.
 */
@NullMarked
public final class VillagerManagerListener implements Listener {

    private final VillagerManagerView view;

    public VillagerManagerListener(VillagerManagerView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof VillagerManagerHolder holder)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        boolean inWindow = clicked != null && clicked.getHolder() instanceof VillagerManagerHolder;
        if (inWindow) {
            event.setCancelled(view.handleClick(holder, event.getSlot()));
        } else if (event.isShiftClick()) {
            // A shift-click from the editor's own inventory would jump the stack into an arbitrary window slot.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof VillagerManagerHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !VillagerManagerLayout.isEditable(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof VillagerManagerHolder managerHolder) {
            view.onClose(managerHolder);
        }
    }
}
