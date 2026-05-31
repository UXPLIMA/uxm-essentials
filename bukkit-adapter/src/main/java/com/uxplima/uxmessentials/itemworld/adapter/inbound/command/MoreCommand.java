package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

/**
 * {@code /more}: fill the held stack to its maximum size. Owned here, not in playerstate (§15.6 deferred it).
 * The fill target is the smaller of the item's natural max stack size and the configured {@code more-cap}, so
 * an operator can bound stack-filling below vanilla; an already-full stack replies
 * {@link ItemworldMessageKey#MORE_ALREADY_FULL} and is left untouched. An empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class MoreCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.more.use";

    public MoreCommand(ItemworldServices services) {
        super(services, "more", SubFeatureGroup.ITEM_UTILS, "Fill the held stack to max.");
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
        Optional<ItemStack> held = heldItem(ctx, player);
        if (held.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        fill(ctx, player, held.get());
        return Command.SINGLE_SUCCESS;
    }

    private void fill(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand) {
        int target =
                Math.min(hand.getMaxStackSize(), Math.max(1, services.config().moreCap()));
        if (hand.getAmount() >= target) {
            reply(ctx, ItemworldMessageKey.MORE_ALREADY_FULL);
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            hand.setAmount(target);
            reply(ctx, ItemworldMessageKey.MORE_FILLED, Map.of("amount", String.valueOf(target)));
        });
    }
}
