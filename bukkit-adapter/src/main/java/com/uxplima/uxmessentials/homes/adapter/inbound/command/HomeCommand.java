package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /home}: open the player's slot-based home grid. The grid is the single entry point for everything a
 * player does with their homes — teleport, create, delete, relocate, rename, re-icon — so this handler only
 * resolves the sender and opens the {@link com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListView}
 * on the viewer's entity thread; the view owns the slot math and every use-case call. Aliases come from the
 * command catalog, not this class.
 */
@NullMarked
public final class HomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.use";

    public HomeCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("home")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open and manage your homes.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.homeList().open(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
