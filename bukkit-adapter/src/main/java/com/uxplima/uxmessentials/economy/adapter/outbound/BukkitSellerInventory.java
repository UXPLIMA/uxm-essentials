package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.economy.application.port.SellerInventory;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/** The Bukkit-backed {@link SellerInventory}: plain stacks into the player's inventory, the overflow at their feet. */
@NullMarked
public final class BukkitSellerInventory implements SellerInventory {

    private final Logger log;

    public BukkitSellerInventory(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean giveBack(PlayerRef seller, Map<String, Integer> items) {
        Player player = Bukkit.getPlayer(seller.uuid());
        if (player == null || !player.isOnline()) {
            return false;
        }
        items.forEach((id, amount) -> {
            Material material = Material.matchMaterial(id);
            if (material == null || !material.isItem()) {
                // Only a material this server no longer knows lands here, and there is nothing left to hand back.
                log.warn("event=sell_return_unknown_item player={} item={} amount={}", seller.uuid(), id, amount);
                return;
            }
            give(player, material, amount);
        });
        return true;
    }

    /** Give in stacks no larger than the material allows, so a large count is never one oversized stack. */
    private static void give(Player player, Material material, int amount) {
        int left = amount;
        while (left > 0) {
            int stack = Math.min(left, material.getMaxStackSize());
            player.getInventory()
                    .addItem(new ItemStack(material, stack))
                    .values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            left -= stack;
        }
    }
}
