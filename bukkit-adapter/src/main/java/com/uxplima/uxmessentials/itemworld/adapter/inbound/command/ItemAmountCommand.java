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
 * {@code /itemamount <amount>} (alias {@code /amount}): set the held stack's amount. The requested amount is
 * clamped between {@code 1} and the smaller of the item's natural max stack size and the configured
 * {@code give-cap}, so a stack can never exceed the give limit; an amount outside that window replies
 * {@link ItemworldMessageKey#AMOUNT_OUT_OF_RANGE}. An empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}. The roulette companion of {@code /more}, which fills to the
 * cap.
 */
@NullMarked
public final class ItemAmountCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemamount.use";

    public ItemAmountCommand(ItemworldServices services) {
        super(services, "itemamount", SubFeatureGroup.ITEM_UTILS, "Set the held stack's amount.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return List.of("amount");
    }

    private int run(CommandContext<CommandSourceStack> ctx, int amount) {
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
        apply(ctx, player, held.get(), amount);
        return Command.SINGLE_SUCCESS;
    }

    private void apply(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, int amount) {
        int cap = Math.min(hand.getMaxStackSize(), Math.max(1, services.config().giveCap()));
        if (amount > cap) {
            reply(ctx, ItemworldMessageKey.AMOUNT_OUT_OF_RANGE, Map.of("cap", String.valueOf(cap)));
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack updated = ItemBuilder.from(hand).amount(amount).build();
            player.getInventory().setItemInMainHand(updated);
            reply(ctx, ItemworldMessageKey.ITEMAMOUNT_SET, Map.of("amount", String.valueOf(amount)));
        });
    }
}
