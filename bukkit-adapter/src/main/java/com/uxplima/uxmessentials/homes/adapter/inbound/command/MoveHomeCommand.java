package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /movehome <name>}: re-anchor an existing home to the player's current position, keeping its name.
 * The {@link com.uxplima.uxmessentials.homes.application.MoveHome} use case rejects a missing name through
 * the sink; this handler maps the name and the current position.
 */
@NullMarked
public final class MoveHomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.move";

    public MoveHomeCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("movehome")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CommandSuggestions.forPlayer(services::ownHomeNames))
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Move a home to your current location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        HomeName name = HomeName.of(ctx.getArgument("name", String.class));
        services.moveHome().move(ref(sender), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }
}
