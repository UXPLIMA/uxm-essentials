package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setmainspawn}: write the player's current location as the global main spawn, the default a world
 * uses when it has no spawn of its own. Shares the operator gate {@code uxmessentials.spawn.set} with
 * {@code /setspawn}; the write itself is one call into {@link
 * com.uxplima.uxmessentials.teleport.application.ResolveSpawn}.
 */
@NullMarked
public final class SetMainSpawnCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawn.set";

    public SetMainSpawnCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setmainspawn")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .then(positionArguments(this::runAt))
                .build();
    }

    @Override
    public String description() {
        return "Set the global main spawn.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.resolveSpawn().setMain(ref(sender), TeleportRefs.positionOf(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int runAt(CommandContext<CommandSourceStack> ctx) {
        com.uxplima.uxmessentials.shared.domain.Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        services.resolveSpawn().setMain(actor(ctx), at);
        return Command.SINGLE_SUCCESS;
    }
}
