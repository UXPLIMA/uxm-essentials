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
 * {@code /sethome [name]}: create a home at the player's current position, or re-anchor an existing one of
 * the same name. With no name the default {@code home} name is used. The quota gate (the highest
 * {@code uxmessentials.home.limit.<n>} the player holds) and the create-vs-move decision are the
 * {@link com.uxplima.uxmessentials.homes.application.SetHome} use case's job; this handler maps the
 * position and the optional name.
 */
@NullMarked
public final class SetHomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.set";
    private static final String DEFAULT_NAME = "home";

    public SetHomeCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sethome")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, DEFAULT_NAME))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> run(ctx, ctx.getArgument("name", String.class))))
                .build();
    }

    @Override
    public String description() {
        return "Create or move a home at your location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, String name) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.setHome().set(ref(sender), HomeName.of(name), position(sender));
        return Command.SINGLE_SUCCESS;
    }
}
