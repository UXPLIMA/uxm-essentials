package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /itemmodel <id>} (alias {@code /custommodeldata}): set the held item's custom model data to an
 * integer id; {@code /itemmodel clear} removes it. The id is validated as a non-negative integer by the
 * Brigadier argument before any mutation, so a non-numeric token never reaches the item. Set and clear report
 * through {@link ItemworldMessageKey#ITEMMODEL_SET} / {@link ItemworldMessageKey#ITEMMODEL_CLEARED}; an empty
 * hand replies {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class ItemModelCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemmodel.use";

    public ItemModelCommand(ItemworldServices services) {
        super(services, "itemmodel", SubFeatureGroup.ITEM_UTILS, "Set the held item's custom model data.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("clear").executes(ctx -> run(ctx, Optional.empty())))
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> run(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "id")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return List.of("custommodeldata");
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<Integer> id) {
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
        apply(ctx, player, held.get(), id);
        return Command.SINGLE_SUCCESS;
    }

    private void apply(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, Optional<Integer> id) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            List<Float> floats = id.map(value -> List.of((float) (int) value)).orElseGet(List::of);
            ItemStack updated =
                    ItemBuilder.from(hand).customModelDataFloats(floats).build();
            player.getInventory().setItemInMainHand(updated);
            if (id.isPresent()) {
                reply(ctx, ItemworldMessageKey.ITEMMODEL_SET, Map.of("id", String.valueOf(id.get())));
            } else {
                reply(ctx, ItemworldMessageKey.ITEMMODEL_CLEARED);
            }
        });
    }
}
