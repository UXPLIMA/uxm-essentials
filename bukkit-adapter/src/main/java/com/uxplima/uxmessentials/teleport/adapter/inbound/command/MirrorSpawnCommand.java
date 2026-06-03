package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /mirrorspawn <world>}: redirect the current world's {@code /spawn} to {@code <world>}'s spawn, so the
 * two share a hub without copying the location. Shares the operator gate {@code uxmessentials.spawn.set} with
 * {@code /setspawn}; the use case rejects an unknown target world and a self-mirror, notifying the actor.
 */
@NullMarked
public final class MirrorSpawnCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawn.set";

    public MirrorSpawnCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mirrorspawn")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("world", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Redirect this world's spawn to another world.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldRef world = BukkitRefs.toRef(sender.getWorld());
        services.resolveSpawn().mirror(ref(sender), world, ctx.getArgument("world", String.class));
        return Command.SINGLE_SUCCESS;
    }
}
