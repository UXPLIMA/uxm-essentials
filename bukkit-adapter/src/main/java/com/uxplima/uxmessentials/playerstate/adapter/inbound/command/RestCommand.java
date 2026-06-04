package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

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
 * {@code /rest [player]} ({@code uxmessentials.rest.use}): reset a player's time-since-rest statistic so the
 * accumulated phantom-spawn pressure clears and phantoms stop. The {@code ResetRest} use case is config-gated
 * (it tells the actor when the feature is disabled). The {@code .others} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node.
 */
@NullMarked
public final class RestCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.rest.use";

    public RestCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rest")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::reset)
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::reset))
                .build();
    }

    @Override
    public String description() {
        return "Reset a player's time-since-rest so phantoms stop.";
    }

    private int reset(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.resetRest().resetFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
