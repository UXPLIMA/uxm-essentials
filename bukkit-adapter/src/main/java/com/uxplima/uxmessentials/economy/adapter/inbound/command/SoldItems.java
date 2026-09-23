package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Taking the items a sale is for, and giving back the ones it does not pay for. Both run on the seller's own thread.
 */
@NullMarked
final class SoldItems {

    private SoldItems() {}

    /** Take up to {@code amount} plain {@code material} from the player, and answer how many were really taken. */
    static int take(Player player, Material material, int amount) {
        Map<Integer, ItemStack> missing = player.getInventory().removeItem(new ItemStack(material, amount));
        return amount - missing.values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    /** Give items back to a player; what does not fit lands at their feet, and a player who left keeps nothing. */
    static void giveBack(PlayerRef seller, Material material, int amount) {
        Player player = Bukkit.getPlayer(seller.uuid());
        if (player == null) {
            return;
        }
        player.getInventory()
                .addItem(new ItemStack(material, amount))
                .values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
