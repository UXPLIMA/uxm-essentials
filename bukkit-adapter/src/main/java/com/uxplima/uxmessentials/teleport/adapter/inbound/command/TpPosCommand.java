package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tppos <x> <y> <z> [world]}: teleport the invoking player to raw coordinates, in their current
 * world unless a world name is supplied. A staff verb with no warmup or cooldown; the hop runs through the
 * async {@code TeleportExecutor}.
 */
@NullMarked
public final class TpPosCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tp.position";

    public TpPosCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tppos")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> run(ctx, null))
                                        .then(Commands.argument("world", StringArgumentType.word())
                                                .executes(ctx -> run(ctx, ctx.getArgument("world", String.class)))))))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to raw coordinates.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, @org.jspecify.annotations.Nullable String worldName) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<WorldRef> world = targetWorld(sender, worldName);
        if (world.isEmpty()) {
            services.notifier().send(ref(sender), TeleportMessageKey.SPAWN_UNRESOLVED);
            return 0;
        }
        org.bukkit.Location facing = TeleportRefs.location(sender);
        Position to = new Position(
                world.get(),
                ctx.getArgument("x", Double.class),
                ctx.getArgument("y", Double.class),
                ctx.getArgument("z", Double.class),
                facing.getYaw(),
                facing.getPitch());
        services.executor().teleport(ref(sender), Destination.at(to), TeleportKind.ADMIN);
        services.notifier().send(ref(sender), TeleportMessageKey.TP_DONE);
        return Command.SINGLE_SUCCESS;
    }

    private Optional<WorldRef> targetWorld(Player sender, @org.jspecify.annotations.Nullable String worldName) {
        if (worldName == null) {
            return Optional.of(BukkitRefs.toRef(sender.getWorld()));
        }
        World world = org.bukkit.Bukkit.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(BukkitRefs.toRef(world));
    }
}
