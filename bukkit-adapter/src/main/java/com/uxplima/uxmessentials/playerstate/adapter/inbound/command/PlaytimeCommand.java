package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /playtime [player]} ({@code uxmessentials.playtime.use}): show a player's total time played. Read-only —
 * the {@code ShowPlaytime} use case reads through the
 * {@link com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo} port. The {@code .others} target is
 * gated by the shared {@code uxmessentials.playerstate.others} node.
 */
@NullMarked
public final class PlaytimeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.playtime.use";

    public PlaytimeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("playtime")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .then(Commands.argument("player", StringArgumentType.word()).executes(this::show))
                .build();
    }

    @Override
    public String description() {
        return "Show a player's total play time.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.showPlaytime().showFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
