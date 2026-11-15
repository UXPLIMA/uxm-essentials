package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * Taking the items a sale is for, on the seller's own thread. Giving back the ones it does not pay for is
 * {@link com.uxplima.uxmessentials.economy.application.ReturnUnsoldItems}' job.
 */
@NullMarked
final class SoldItems {

    private SoldItems() {}

    /** Take up to {@code amount} plain {@code material} from the player, and answer how many were really taken. */
    static int take(Player player, Material material, int amount) {
        Map<Integer, ItemStack> missing = player.getInventory().removeItem(new ItemStack(material, amount));
        return amount - missing.values().stream().mapToInt(ItemStack::getAmount).sum();
    }
}
