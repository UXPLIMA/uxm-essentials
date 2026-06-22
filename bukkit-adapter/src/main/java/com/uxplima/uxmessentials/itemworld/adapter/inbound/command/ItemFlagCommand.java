package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /itemflag <flag> <on|off>}: toggle an item meta flag (e.g. {@code hide_enchants}) on the held item.
 * The flag token resolves to an {@link ItemFlag} at the boundary, replying
 * {@link ItemworldMessageKey#ITEMFLAG_UNKNOWN} on an unknown token; {@code on} adds the flag, {@code off}
 * removes it, both reported through {@link ItemworldMessageKey#ITEMFLAG_TOGGLED}. An empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class ItemFlagCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemflag.use";

    public ItemFlagCommand(ItemworldServices services) {
        super(services, "itemflag", SubFeatureGroup.ITEM_UTILS, "Toggle an item meta flag.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(itemFlagArgument()
                        .then(Commands.literal("on").executes(ctx -> run(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> run(ctx, false))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, boolean on) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<ItemStack> held = heldItem(ctx, player);
        if (held.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        toggle(ctx, player, held.get(), on);
        return Command.SINGLE_SUCCESS;
    }

    private void toggle(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, boolean on) {
        String token = ctx.getArgument("flag", String.class);
        Optional<ItemFlag> flag = BukkitItemResolver.itemFlag(token);
        if (flag.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ITEMFLAG_UNKNOWN, Map.of("flag", token));
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack built = on
                    ? ItemBuilder.from(hand).flags(flag.get()).build()
                    : ItemBuilder.from(hand).removeFlags(flag.get()).build();
            player.getInventory().setItemInMainHand(built);
            reply(ctx, ItemworldMessageKey.ITEMFLAG_TOGGLED, Map.of("flag", token, "state", on ? "on" : "off"));
        });
    }
}
