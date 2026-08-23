package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /rest [player]} ({@code uxmessentials.rest.use}): reset a player's time-since-rest statistic so the
 * accumulated phantom-spawn pressure clears and phantoms stop. The {@code ResetRest} use case is config-gated
 * (it tells the actor when the feature is disabled). The {@code .others} target is gated by the shared
 * {@code uxmessentials.rest.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node.
 */
@NullMarked
public final class RestCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.rest.use";

    public RestCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.rest.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rest")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::reset)
                .then(PlayerTargets.players("player").executes(this::reset))
                .build();
    }

    @Override
    public String description() {
        return "Reset a player's time-since-rest so phantoms stop.";
    }

    private int reset(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            services.resetRest().resetFor(actor(ctx), target);
        }
        return Command.SINGLE_SUCCESS;
    }
}
