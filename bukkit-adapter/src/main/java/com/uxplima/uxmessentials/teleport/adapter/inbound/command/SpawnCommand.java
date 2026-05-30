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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /spawn} and {@code /spawn <name>}: teleport to the resolved (possibly mirrored) default spawn for
 * the player's current world, or to an operator-defined named spawn. Resolution and the gated hop are the
 * {@link com.uxplima.uxmessentials.teleport.application.ResolveSpawn} use case's job; the named form is
 * gated by its own permission.
 */
@NullMarked
public final class SpawnCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawn.use";
    private static final String NAMED_PERMISSION = "uxmessentials.spawn.named";

    public SpawnCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("spawn")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runDefault)
                .then(Commands.argument("name", StringArgumentType.word())
                        .requires(src -> src.getSender().hasPermission(NAMED_PERMISSION))
                        .executes(this::runNamed))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to spawn.";
    }

    private int runDefault(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        WorldRef world = BukkitRefs.toRef(sender.getWorld());
        services.resolveSpawn().toDefaultSpawn(who, world);
        return Command.SINGLE_SUCCESS;
    }

    private int runNamed(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.resolveSpawn().toNamedSpawn(ref(sender), ctx.getArgument("name", String.class));
        return Command.SINGLE_SUCCESS;
    }
}
