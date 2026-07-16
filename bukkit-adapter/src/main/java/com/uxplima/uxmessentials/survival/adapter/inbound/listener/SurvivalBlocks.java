package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import com.uxplima.uxmessentials.survival.domain.BlockPos;
import com.uxplima.uxmessentials.survival.domain.ConnectedBlockSearch;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-side mechanics both harvesting listeners share: run the pure {@link ConnectedBlockSearch} over a live
 * world, break the connected group, and apply the durability and hunger costs.
 *
 * <h2>Folia</h2>
 * The cascade breaks each connected block inline on the {@code BlockBreakEvent} thread. That thread already owns the
 * broken block's region, and the search is bounded ({@code max-blocks}) and grows only through immediate neighbours,
 * so every block it reaches sits in the same local neighbourhood the event thread owns — the region the task guidance
 * says is safe to mutate here without a scheduler hop. The one piece of work that must outlive the event, the
 * tree-feller sapling replant, is deferred through the {@code Scheduler} port by the listener that needs it.
 */
@NullMarked
final class SurvivalBlocks {

    private SurvivalBlocks() {}

    /**
     * Break every block connected to {@code origin} that shares its material, up to the search's cap, leaving the
     * origin itself to the event's own break. Drops respect {@code tool} (enchantments, silk touch).
     *
     * @return how many blocks were broken, the origin excluded
     */
    static int breakConnected(ConnectedBlockSearch search, Block origin, ItemStack tool) {
        Material type = origin.getType();
        World world = origin.getWorld();
        BlockPos start = new BlockPos(origin.getX(), origin.getY(), origin.getZ());
        List<BlockPos> group = search.from(
                start, pos -> world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == type);
        int broken = 0;
        for (BlockPos pos : group) {
            if (pos.equals(start)) {
                continue; // the origin is broken by the vanilla event itself; the cascade takes only the extras
            }
            world.getBlockAt(pos.x(), pos.y(), pos.z()).breakNaturally(tool);
            broken++;
        }
        return broken;
    }

    /** Add {@code damage} durability points to the player's main-hand tool, wearing it out when it runs out. */
    static void drainMainHand(Player player, int damage) {
        if (damage <= 0) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        int max = tool.getType().getMaxDurability();
        if (max <= 0 || !(tool.getItemMeta() instanceof Damageable meta)) {
            return; // an empty hand or a non-damageable tool takes no wear
        }
        int worn = meta.getDamage() + damage;
        if (worn >= max) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        meta.setDamage(worn);
        tool.setItemMeta((ItemMeta) meta);
        player.getInventory().setItemInMainHand(tool);
    }

    /** Add {@code perBlock} exhaustion for each of {@code blocks} broken, so a large harvest costs hunger. */
    static void addExhaustion(Player player, double perBlock, int blocks) {
        if (perBlock <= 0 || blocks <= 0) {
            return;
        }
        player.setExhaustion(player.getExhaustion() + (float) (perBlock * blocks));
    }

    /** Whether {@code material} is one of the six axe tools (tree-feller's {@code require-axe} gate). */
    static boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }
}
