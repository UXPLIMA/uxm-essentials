package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hat}: wear the held item as a helmet. Owned here, not in playerstate (§15.6 deferred it). The held
 * item is swapped into the helmet slot and whatever was on the head returns to the hand, so the action is
 * reversible. An empty hand replies {@link ItemworldMessageKey#HAT_NO_ITEM}.
 */
@NullMarked
public final class HatCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.hat.use";

    public HatCommand(ItemworldServices services) {
        super(services, "hat", SubFeatureGroup.ITEM_UTILS, "Wear the held item as a hat.");
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            reply(ctx, ItemworldMessageKey.HAT_NO_ITEM);
            return Command.SINGLE_SUCCESS;
        }
        wear(ctx, player);
        return Command.SINGLE_SUCCESS;
    }

    private void wear(CommandContext<CommandSourceStack> ctx, Player player) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            PlayerInventory inventory = player.getInventory();
            ItemStack hand = inventory.getItemInMainHand();
            @Nullable ItemStack previousHelmet = inventory.getHelmet();
            inventory.setHelmet(hand);
            inventory.setItemInMainHand(previousHelmet == null ? new ItemStack(Material.AIR) : previousHelmet);
            reply(
                    ctx,
                    ItemworldMessageKey.HAT_WORN,
                    Map.of("item", hand.getType().getKey().toString()));
        });
    }
}
