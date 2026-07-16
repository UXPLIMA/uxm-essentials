package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Routes the click, drag, close, quit, and world-change events for trade windows, recognised by their
 * {@link TradeHolder}. The click policy keeps the window loss-safe and anti-scam: a click on the confirm slot runs the
 * confirm, a click or drag that stays within the viewer's own editable offer (or their own inventory) is allowed and
 * schedules an offer re-read, and everything else — a shift-click or double-click that could shove an item onto a
 * control or the read-only mirror, a click on the mirror or filler — is cancelled outright, so no stack ever lands
 * where it cannot be returned. Close, disconnect, and world change all funnel into the view's return path.
 */
@NullMarked
public final class TradeListener implements Listener {

    private final TradeView view;
    private final TradeLayout layout;

    public TradeListener(TradeView view, TradeLayout layout) {
        this.view = Objects.requireNonNull(view, "view");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        TradeHolder holder = holderOf(top);
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
            return;
        }
        view.scheduleSync(holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        TradeHolder holder = holderOf(top);
        if (holder == null) {
            return;
        }
        int topSize = top.getSize();
        if (event.getRawSlots().stream().anyMatch(raw -> raw < topSize && !layout.isEditable(raw))) {
            event.setCancelled(true);
            return;
        }
        view.scheduleSync(holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        TradeHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null) {
            view.onClose(holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        view.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        view.onLeave(event.getPlayer().getUniqueId());
    }

    private boolean editAllowed(InventoryClickEvent event, Inventory top) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
            // An unbounded push into the top could land on a control or the mirror, so deny it outright.
            return false;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return false;
        }
        if (!clicked.equals(top)) {
            return true; // a click in the viewer's own inventory is theirs to make
        }
        return layout.isEditable(event.getRawSlot());
    }

    private static @Nullable TradeHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof TradeHolder trade ? trade : null;
    }
}
