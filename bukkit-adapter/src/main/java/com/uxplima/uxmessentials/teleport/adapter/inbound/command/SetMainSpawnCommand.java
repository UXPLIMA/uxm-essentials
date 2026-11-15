package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setmainspawn [scope <scope>] [<world> <x> <y> <z> [yaw pitch]]}: write the main spawn, the default a
 * world uses when it has no spawn of its own. Without a scope it writes this server's default scope; {@code scope
 * network} writes the spawn every server falls back to, and {@code scope server|server:<id>|set:<name>} writes
 * that scope's. Shares the operator gate {@code uxmessentials.spawn.set} with {@code /setspawn}; the write itself
 * is one call into {@link com.uxplima.uxmessentials.teleport.application.ResolveSpawn}.
 */
@NullMarked
public final class SetMainSpawnCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawn.set";
    private static final String SCOPE = "scope";

    public SetMainSpawnCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setmainspawn")
                .requires(Gate.permission(PERMISSION))
                .executes(this::run)
                .then(Commands.literal("scope")
                        .then(Commands.argument(SCOPE, Args.token())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.resolveSpawn().scopeSuggestions()))
                                .executes(this::runScoped)
                                .then(positionArguments(this::runScopedAt))))
                .then(positionArguments(this::runAt))
                .build();
    }

    @Override
    public String description() {
        return "Set the main spawn for worlds without their own.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        Position at = TeleportRefs.positionOf(sender);
        // The write reaches the database, so it leaves the command thread; the reply follows from there.
        services.scheduler().async(() -> services.resolveSpawn().setMain(who, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runScoped(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        Position at = TeleportRefs.positionOf(sender);
        String scope = ctx.getArgument(SCOPE, String.class);
        services.scheduler().async(() -> services.resolveSpawn().setMain(who, scope, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runScopedAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        String scope = ctx.getArgument(SCOPE, String.class);
        services.scheduler().async(() -> services.resolveSpawn().setMain(who, scope, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        PlayerRef who = actor(ctx);
        services.scheduler().async(() -> services.resolveSpawn().setMain(who, at));
        return Command.SINGLE_SUCCESS;
    }
}
