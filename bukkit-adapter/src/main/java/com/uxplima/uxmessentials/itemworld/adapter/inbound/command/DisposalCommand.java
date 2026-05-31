package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /disposal} (alias {@code /trash}): open a throwaway window — anything left inside when it closes is
 * discarded. The window is a fresh, holder-less double-chest inventory backed by nothing: items dropped into
 * it are never written to a real container, so closing the view silently drops them. Opening is entity-bound,
 * so it is scheduled on the player's region thread through the kernel {@code Scheduler}, then reported through
 * {@link ItemworldMessageKey#DISPOSAL_OPENED}.
 */
@NullMarked
public final class DisposalCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.disposal.use";
    private static final int DISPOSAL_SLOTS = 54;

    public DisposalCommand(ItemworldServices services) {
        super(services, "disposal", SubFeatureGroup.CLEANUP, "Open a throwaway window.");
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

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("trash");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = ref(player);
        services.kernel().scheduler().onEntity(viewer, () -> {
            Inventory disposal = Bukkit.createInventory((InventoryHolder) null, DISPOSAL_SLOTS);
            player.openInventory(disposal);
            reply(ctx, ItemworldMessageKey.DISPOSAL_OPENED);
        });
        return Command.SINGLE_SUCCESS;
    }
}
