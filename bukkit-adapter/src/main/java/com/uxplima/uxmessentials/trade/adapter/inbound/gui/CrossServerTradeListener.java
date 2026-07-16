package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Routes the click, drag, close, and quit events for a cross-server trade window, recognised by its
 * {@link CrossTradeHolder}, keeping it loss-safe: a click on the confirm slot escrows the side, a click or drag that
 * stays within the viewer's own editable offer (or their own inventory) is allowed, and everything else — a shift-move
 * or collect that could shove a stack onto a control or the read-only mirror — is cancelled outright, so no stack ever
 * lands where it cannot be returned. A close or disconnect funnels into the view's return-and-abort path.
 */
@NullMarked
public final class CrossServerTradeListener implements Listener {

    private final CrossServerTradeView view;
    private final TradeLayout layout;

    public CrossServerTradeListener(CrossServerTradeView view, TradeLayout layout) {
        this.view = Objects.requireNonNull(view, "view");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        CrossTradeHolder holder = holderOf(top);
        if (holder == null) {
            return;
        }
        if (event.getRawSlot() == layout.confirmSlot()) {
            event.setCancelled(true);
            view.confirm(holder);
            return;
        }
        if (!editAllowed(event, top)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (holderOf(top) == null) {
            return;
        }
        int topSize = top.getSize();
        if (event.getRawSlots().stream().anyMatch(raw -> raw < topSize && !layout.isEditable(raw))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        CrossTradeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null) {
            view.onClose(holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        view.onQuit(event.getPlayer());
    }

    private boolean editAllowed(InventoryClickEvent event, Inventory top) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
            return false;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return false;
        }
        if (!clicked.equals(top)) {
            return true;
        }
        return layout.isEditable(event.getRawSlot());
    }

    private static @Nullable CrossTradeHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof CrossTradeHolder cross ? cross : null;
    }
}
