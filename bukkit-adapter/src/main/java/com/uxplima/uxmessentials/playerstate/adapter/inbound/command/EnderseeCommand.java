package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /endersee [player]} ({@code uxmessentials.endersee.use}): open a live view of another player's ender
 * chest in your own screen — gated for a named target by the shared {@code uxmessentials.playerstate.others}
 * node. The {@code OpenContainer} use case owns the open and the viewer confirmation.
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
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::view))
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
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.openContainer().openEnderChest(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
