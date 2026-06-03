package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /powertoollist}: print the commands bound to the held item by {@code /powertool}. A pure read of the
 * binding {@link PdcPowertoolStore#commandsOf(ItemStack) stamped on the item PDC}, so it reuses the existing
 * {@code uxmessentials.powertool.use} node and keeps no state of its own. The held item is read on the owner's
 * region thread (the store touches a live {@link ItemStack}).
 *
 * <p>An empty hand replies {@link ItemworldMessageKey#POWERTOOL_NO_ITEM}; a held item with no binding replies
 * {@link ItemworldMessageKey#POWERTOOL_LIST_EMPTY}; otherwise a {@link ItemworldMessageKey#POWERTOOL_LIST_HEADER}
 * is followed by one {@link ItemworldMessageKey#POWERTOOL_LIST_ENTRY} per bound command line.
 */
@NullMarked
public final class PowertoolListCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.powertool.use";

    private final PdcPowertoolStore store;

    public PowertoolListCommand(ItemworldServices services, PdcPowertoolStore store) {
        super(services, "powertoollist", SubFeatureGroup.POWERTOOL, "List the held item's powertool bindings.");
        this.store = Objects.requireNonNull(store, "store");
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
        services.kernel().scheduler().onEntity(ref(player), () -> list(ctx, player));
        return Command.SINGLE_SUCCESS;
    }

    private void list(CommandContext<CommandSourceStack> ctx, Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            reply(ctx, ItemworldMessageKey.POWERTOOL_NO_ITEM);
            return;
        }
        Optional<List<String>> bound = store.commandsOf(hand);
        if (bound.isEmpty()) {
            reply(ctx, ItemworldMessageKey.POWERTOOL_LIST_EMPTY);
            return;
        }
        reply(ctx, ItemworldMessageKey.POWERTOOL_LIST_HEADER);
        for (String command : bound.get()) {
            reply(ctx, ItemworldMessageKey.POWERTOOL_LIST_ENTRY, Map.of("command", command));
        }
    }
}
