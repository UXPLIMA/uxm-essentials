package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

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
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setpwarp <name>}: create a player-owned warp at the player's current position, or re-anchor an
 * existing one of the same name. The create-vs-move decision and the per-owner limit check are the
 * {@link com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp} use case's job; this handler maps
 * the name and the position. The {@code uxmessentials.pwarp.set} node guards the command.
 */
@NullMarked
public final class SetPlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pwarp.set";

    public SetPlayerWarpCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setpwarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Create or move a player warp at your location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerWarpName name = PlayerWarpName.of(ctx.getArgument("name", String.class));
        services.setPlayerWarp().set(ref(sender), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }
}
