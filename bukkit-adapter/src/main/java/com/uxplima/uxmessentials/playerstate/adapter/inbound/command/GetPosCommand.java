package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /getpos [player]} (aliases {@code /coords}, {@code /whereami}, {@code uxmessentials.getpos.use}): show
 * a player's world, block coordinates, and look direction. Read-only — the {@code ShowPosition} use case reads
 * through the {@link com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo} port and renders a
 * click-to-copy line. The {@code .others} target is gated by the shared {@code uxmessentials.playerstate.others}
 * node.
 */
@NullMarked
public final class GetPosCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.getpos.use";

    public GetPosCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("getpos")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::show))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("coords", "whereami");
    }

    @Override
    public String description() {
        return "Show a player's coordinates.";
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
        services.showPosition().showFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
