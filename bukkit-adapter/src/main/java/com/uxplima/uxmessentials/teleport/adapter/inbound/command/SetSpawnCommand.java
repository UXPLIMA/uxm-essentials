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
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setspawn} and {@code /setspawn <name>}: write the player's current location as the world's
 * default spawn or as a named spawn, through the spawn directory. The operator gate is its own permission
 * node; the write itself is one call into {@link com.uxplima.uxmessentials.teleport.application.ResolveSpawn}.
 */
@NullMarked
public final class SetSpawnCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawn.set";

    public SetSpawnCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setspawn")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runDefault)
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::runNamed))
                .build();
    }

    @Override
    public String description() {
        return "Set the server spawn.";
    }

    private int runDefault(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldRef world = BukkitRefs.toRef(sender.getWorld());
        services.resolveSpawn().setDefaultSpawn(world, position(sender));
        services.notifier().send(ref(sender), TeleportMessageKey.SPAWN_SET);
        return Command.SINGLE_SUCCESS;
    }

    private int runNamed(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.resolveSpawn().setNamedSpawn(ctx.getArgument("name", String.class), position(sender));
        services.notifier().send(ref(sender), TeleportMessageKey.SPAWN_SET);
        return Command.SINGLE_SUCCESS;
    }

    private static Position position(Player sender) {
        return TeleportRefs.positionOf(sender);
    }
}
