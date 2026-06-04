package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /gamemode <mode> [player]} ({@code uxmessentials.gamemode.use}): set a player's game mode. The mode
 * is parsed from the full name, the short alias, or the numeric id; an unrecognised mode is rejected with
 * {@link PlayerstateMessageKey#GAMEMODE_INVALID}. The {@code .others} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node. The {@code /gmc /gms /gma /gmsp} shortcuts are separate
 * fixed-mode commands ({@link GamemodeAliasCommand}), not aliases of this literal, because each pins a mode.
 */
@NullMarked
public final class GamemodeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.gamemode.use";

    public GamemodeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("gamemode")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(this::setMode)
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::setMode)))
                .build();
    }

    @Override
    public String description() {
        return "Set game mode.";
    }

    private int setMode(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<GameModeRef> mode = GameModeRef.parse(ctx.getArgument("mode", String.class));
        if (mode.isEmpty()) {
            feedback.send(sender, PlayerstateMessageKey.GAMEMODE_INVALID, Map.of());
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.setGamemode().setFor(ref(sender), target.get(), mode.get());
        return Command.SINGLE_SUCCESS;
    }
}
