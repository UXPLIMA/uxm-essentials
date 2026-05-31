package com.uxplima.uxmessentials.kits.application.port;

import java.util.List;

import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Delivers a kit's items into a player's inventory. The adapter deserializes each {@link KitItem}'s opaque
 * payload back into a Bukkit {@code ItemStack} and adds it, overflowing to the ground only if the inventory
 * is full — so the {@code :core} layer asks for items to be granted without ever touching an item type.
 *
 * <p>The grant is the last step of a claim, run after every gate (permission, cooldown, one-time, cost) has
 * passed, so it does not itself decide whether the claim is allowed. It reports whether everything fit so
 * the use case can surface an inventory-full notice; the items are still delivered (dropped at the player's
 * feet) when they did not all fit, matching the common kit-plugin behaviour.
 */
public interface KitGranter {

    /**
     * Grant {@code items} to {@code recipient}. Returns the outcome so the claim use case can warn when the
     * inventory could not hold everything; a {@code false} {@code fitInInventory} still means the items were
     * delivered (overflow dropped at the player's feet), not that the claim failed.
     */
    Grant grant(PlayerRef recipient, List<KitItem> items);

    /**
     * The result of a grant.
     *
     * @param fitInInventory true when every stack fit in the inventory, false when some overflowed to the
     *     ground
     */
    record Grant(boolean fitInInventory) {

        /** Everything fit in the inventory. */
        public static Grant complete() {
            return new Grant(true);
        }

        /** Some stacks overflowed to the ground. */
        public static Grant overflowed() {
            return new Grant(false);
        }
    }
}
