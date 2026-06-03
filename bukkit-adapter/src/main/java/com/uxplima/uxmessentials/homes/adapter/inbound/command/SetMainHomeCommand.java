package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.List;

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
 * {@code /setmainhome <name>} (alias {@code /mainhome}): choose which of the player's homes plain
 * {@code /home} (no name) teleports to. The {@link com.uxplima.uxmessentials.homes.application.SetMainHome}
 * use case validates the name against the player's homes and rejects an unknown one through the sink; this
 * handler only maps the typed name to the use-case call.
 */
@NullMarked
public final class SetMainHomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.setmain";

    public SetMainHomeCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setmainhome")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Set your main /home destination.";
    }

    @Override
    public List<String> aliases() {
        return List.of("mainhome");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        HomeName name = HomeName.of(ctx.getArgument("name", String.class));
        services.setMainHome().setMain(ref(sender), name);
        return Command.SINGLE_SUCCESS;
    }
}
