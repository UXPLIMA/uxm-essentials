package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /endersee [player]} ({@code uxmessentials.endersee.use}): open a view of another player's ender chest in
 * your own screen — gated for a named target by the shared {@code uxmessentials.playerstate.others} node. The
 * target may be online (the live ender chest) or offline (read from their stored player data); the
 * {@code OpenContainer} use case owns the open and the viewer confirmation, and the adapter routes by online
 * state.
 */
@NullMarked
public final class EnderseeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.endersee.use";

    public EnderseeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("endersee")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::view)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions())
                        .executes(this::view))
                .build();
    }

    @Override
    public String description() {
        return "View a player's ender chest.";
    }

    private int view(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveStorageTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.openContainer().openEnderChest(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
