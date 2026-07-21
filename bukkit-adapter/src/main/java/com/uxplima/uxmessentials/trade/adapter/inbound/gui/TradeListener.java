package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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
    private final Set<Material> blacklist;

    public TradeListener(TradeView view, TradeLayout layout, Set<Material> blacklist) {
        this.view = Objects.requireNonNull(view, "view");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.blacklist = Set.copyOf(Objects.requireNonNull(blacklist, "blacklist"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        TradeHolder holder = holderOf(top);
        if (holder == null) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == layout.confirmSlot()) {
            event.setCancelled(true);
            view.confirm(holder);
            return;
        }
        if (raw < top.getSize() && layout.isMoneySlot(raw)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                // A left-click stakes the selected currency; a right-click cycles to the next allowed currency, so the
                // single money slot still covers a multi-currency economy without losing any currency.
                if (event.isRightClick()) {
                    view.cycleCurrency(holder);
                } else {
                    view.promptMoney(player, holder);
                }
            }
            return;
        }
        if (raw < top.getSize() && layout.isExperienceSlot(raw)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                view.promptExperience(player, holder);
            }
            return;
        }
        if (!editAllowed(event, top)) {
            event.setCancelled(true);
            return;
        }
        if (placesBlacklisted(event, top)) {
            event.setCancelled(true);
            view.refuseBlacklisted(holder);
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
        boolean intoEditable = event.getRawSlots().stream().anyMatch(raw -> raw < topSize && layout.isEditable(raw));
        if (event.getRawSlots().stream().anyMatch(raw -> raw < topSize && !layout.isEditable(raw))) {
            event.setCancelled(true);
            return;
        }
        if (intoEditable && isBlacklisted(event.getOldCursor())) {
            event.setCancelled(true);
            view.refuseBlacklisted(holder);
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

    /**
     * Whether this click would move a blacklisted item into the viewer's own editable region — the only way a fresh
     * stack enters the window. A place or cursor-swap onto an editable slot carries the item on the cursor; a hotbar
     * number-key swap carries the hotbar item. Shift-moves into the top are already denied outright by
     * {@link #editAllowed}, so they never reach here.
     */
    private boolean placesBlacklisted(InventoryClickEvent event, Inventory top) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top) || !layout.isEditable(event.getRawSlot())) {
            return false;
        }
        if (isBlacklisted(event.getCursor())) {
            return true;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            return isBlacklisted(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        }
        return false;
    }

    private boolean isBlacklisted(@Nullable ItemStack stack) {
        return stack != null && !stack.getType().isAir() && blacklist.contains(stack.getType());
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
