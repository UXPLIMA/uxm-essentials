package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarp <name> [owner]}: teleport to a player-warp. With no owner the sender warps to their own warp;
 * with an owner they warp to that player's warp, permitted only when it is public, and the owner is resolved
 * online-first then from the profile cache so a public warp stays reachable while its owner is offline. The
 * {@code visibility public <name>} and {@code visibility private <name>} subtrees (gated by
 * {@code uxmessentials.pwarp.public}) flip a warp's visibility. The ownership/public gate, the visibility flip,
 * and the delegated gated hop are the player-warps use cases' job; this handler maps the arguments and resolves
 * the owner. The {@code uxmessentials.pwarp.use} node guards the command. The visibility toggles live under the
 * {@code visibility} literal so a warp named {@code public} or {@code private} is reachable through the bare
 * {@code name} argument rather than being shadowed by a keyword.
 */
@NullMarked
public final class PlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pwarp.use";
    private static final String PUBLIC_PERMISSION = "uxmessentials.pwarp.public";

    public PlayerWarpCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pwarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("visibility")
                        .requires(src -> src.getSender().hasPermission(PUBLIC_PERMISSION))
                        .then(Commands.literal("public")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                        .executes(this::makePublic)))
                        .then(Commands.literal("private")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                        .executes(this::makePrivate))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                        .executes(this::useOwn)
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .suggests(CommandSuggestions.onlinePlayers())
                                .executes(this::useOther)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to your own or a player's public warp.";
    }

    private int useOwn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.usePlayerWarp().use(ref(sender), PlayerWarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int useOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String ownerName = ctx.getArgument("owner", String.class);
        Optional<PlayerRef> owner = services.players().findByName(ownerName);
        if (owner.isEmpty()) {
            unknownPlayer(ctx.getSource().getSender(), ownerName);
            return 0;
        }
        services.usePlayerWarp()
                .useFor(ref(sender), owner.get(), PlayerWarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int makePublic(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.visibility().setPublic(ref(sender), PlayerWarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int makePrivate(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.visibility().setPrivate(ref(sender), PlayerWarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
