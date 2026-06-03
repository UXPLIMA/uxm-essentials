package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /homes}: with no argument open the read-only browse menu listing the player's own homes (a uxmLib
 * {@code PaginatedGui}, one display icon per home); {@code /homes list} prints the same homes as the clickable
 * chat list. Both paths share the {@link com.uxplima.uxmessentials.homes.application.ListHomes} read so they
 * never disagree. A console source has no inventory, so bare {@code /homes} falls back to the chat list. The
 * base {@code uxmessentials.home.list} node guards the command.
 */
@NullMarked
public final class HomesCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.list";

    public HomesCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("homes")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("list").executes(this::runList))
                .executes(this::runMenu)
                .build();
    }

    @Override
    public String description() {
        return "Browse your homes.";
    }

    private int runMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            // A console has no inventory to open a menu in; show the chat list instead.
            return runList(ctx);
        }
        PlayerRef viewer = ref(player);
        List<Home> homes = services.listHomes().homesOf(viewer);
        services.homeMenu().open(player, viewer, homes);
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listHomes().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
