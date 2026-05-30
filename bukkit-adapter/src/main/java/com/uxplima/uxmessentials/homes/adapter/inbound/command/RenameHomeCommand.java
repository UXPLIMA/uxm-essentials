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
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /renamehome <old> <new>}: rename one of the player's homes without re-setting it. The
 * {@link com.uxplima.uxmessentials.homes.application.RenameHome} use case rejects a missing source or a
 * taken target through the sink; this handler maps the two name arguments.
 */
@NullMarked
public final class RenameHomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.rename";

    public RenameHomeCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("renamehome")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("old", StringArgumentType.word())
                        .then(Commands.argument("new", StringArgumentType.word())
                                .executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Rename one of your homes.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        HomeName from = HomeName.of(ctx.getArgument("old", String.class));
        HomeName to = HomeName.of(ctx.getArgument("new", String.class));
        services.renameHome().rename(ref(sender), from, to);
        return Command.SINGLE_SUCCESS;
    }
}
