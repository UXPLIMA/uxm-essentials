package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /repairall}: repair every damaged item in the player's inventory at once. Owned here in the itemworld
 * surface, gated by the {@code uxmessentials.repair.itemworld} node (it shares the playerstate
 * {@code repair.all} semantics; both nodes gate the same action). The whole-inventory scan resets durability
 * on every {@link Damageable} item and reports the count repaired through
 * {@link ItemworldMessageKey#REPAIRALL_DONE}; a fully-undamaged inventory reports a count of zero.
 */
@NullMarked
public final class RepairAllCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.repair.itemworld";

    public RepairAllCommand(ItemworldServices services) {
        super(services, "repairall", SubFeatureGroup.ITEM_UTILS, "Repair the whole inventory.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            int repaired = repairContents(player);
            reply(ctx, ItemworldMessageKey.REPAIRALL_DONE, Map.of("count", String.valueOf(repaired)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int repairContents(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int repaired = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
                inventory.setItem(slot, ItemBuilder.from(item).damage(0).build());
                repaired++;
            }
        }
        return repaired;
    }
}
