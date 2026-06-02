package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /unbreakable [true|false]}: set the held item's unbreakable flag, or flip it when called with no
 * argument. With an explicit {@code true}//{@code false} the flag is set to that value; with no argument the
 * current state is read and inverted. The new state is reported through
 * {@link ItemworldMessageKey#UNBREAKABLE_SET}; an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class UnbreakableCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.unbreakable.use";

    public UnbreakableCommand(ItemworldServices services) {
        super(services, "unbreakable", SubFeatureGroup.ITEM_UTILS, "Toggle the held item's unbreakable flag.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> run(ctx, Optional.of(BoolArgumentType.getBool(ctx, "value")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<Boolean> requested) {
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
        apply(ctx, player, held.get(), requested);
        return Command.SINGLE_SUCCESS;
    }

    private void apply(
            CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, Optional<Boolean> requested) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            boolean current = hand.getItemMeta().isUnbreakable();
            boolean state = requested.orElse(!current);
            ItemStack updated = ItemBuilder.from(hand).unbreakable(state).build();
            player.getInventory().setItemInMainHand(updated);
            reply(ctx, ItemworldMessageKey.UNBREAKABLE_SET, Map.of("state", state ? "true" : "false"));
        });
    }
}
