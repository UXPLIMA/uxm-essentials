package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.function.IntPredicate;

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import org.jspecify.annotations.NullMarked;

/**
 * The dupe-safe click/drag policy shared by every managed "look at someone's storage" menu — {@code /invsee},
 * {@code /endersee}, and their offline counterparts. The menu is a private copy the viewer edits, so the only
 * thing the policy must prevent is an item being pushed into a slot the menu does not mirror back (a filler pane),
 * which would silently swallow it. Each menu supplies its own {@code editable} predicate over top-inventory slots;
 * everything below is identical regardless of what the menu mirrors.
 */
@NullMarked
final class ManagedMenuPolicy {

    private ManagedMenuPolicy() {}

    /** Whether an editing viewer's click is safe: it must not move an item into a non-editable top slot. */
    static boolean clickAllowed(InventoryClickEvent event, IntPredicate editable) {
        if (unboundedIntoTop(event.getAction())) {
            // A shift-click or double-click collect can land an item anywhere in the top with no named
            // destination, so it could park on a filler slot; deny it outright.
            return false;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return true; // a click in the viewer's own inventory is theirs to make
        }
        // A click that lands on a specific top slot (including a hotbar swap onto it) is fine when editable.
        return editable.test(event.getSlot());
    }

    /** Whether a drag touches any non-editable top slot (and so must be cancelled). */
    static boolean dragTouchesNonEditable(InventoryDragEvent event, IntPredicate editable) {
        int topSize = event.getView().getTopInventory().getSize();
        return event.getRawSlots().stream().anyMatch(raw -> raw < topSize && !editable.test(raw));
    }

    /** Actions that can push an item into the top inventory without naming a destination slot. */
    private static boolean unboundedIntoTop(InventoryAction action) {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR;
    }
}
