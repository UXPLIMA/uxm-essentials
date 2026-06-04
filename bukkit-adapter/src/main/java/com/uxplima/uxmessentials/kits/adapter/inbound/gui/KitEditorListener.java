package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Routes the close event for {@code /kiteditor} windows, recognised by their {@link KitEditorHolder}. The editor
 * is fully editable — the staff member may take, place, and rearrange items freely — so there is no click or drag
 * policy to enforce; the only thing that matters is the final state, which {@link KitEditorView#onClose}
 * encodes back into the kit's items on close. A window is persisted at most once: the view's open-set claim makes
 * a close-and-flush race save exactly one of them.
 */
@NullMarked
public final class KitEditorListener implements Listener {

    private final KitEditorView view;

    public KitEditorListener(KitEditorView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        KitEditorHolder holder = holderOf(event.getView().getTopInventory());
        if (holder != null) {
            view.onClose(holder);
        }
    }

    private static @Nullable KitEditorHolder holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof KitEditorHolder editor ? editor : null;
    }
}
