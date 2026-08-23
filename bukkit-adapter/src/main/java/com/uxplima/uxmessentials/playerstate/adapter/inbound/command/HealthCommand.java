package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /health <amount> [player]} ({@code uxmessentials.health.use}): set a player's health to a specific
 * value. The amount is bounded below by Brigadier and floored at {@code 0} in the domain ({@link HealthLevel});
 * the adapter caps it to the player's live maximum health, so a value of {@code 0} kills. Distinct from
 * {@code /heal}, which always restores to full. The {@code .others} target is gated by the shared
 * {@code uxmessentials.health.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node; the {@code SetHealth} use case owns the effect and feedback.
 */
@NullMarked
public final class HealthCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.health.use";
    private static final int MAX_HEALTH_INPUT = 2048;

    public HealthCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.health.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("health")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, MAX_HEALTH_INPUT))
                        .executes(this::set)
                        .then(PlayerTargets.players("player").executes(this::set)))
                .build();
    }

    @Override
    public String description() {
        return "Set a player's health.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        HealthLevel level = HealthLevel.of(ctx.getArgument("amount", Integer.class));
        for (PlayerRef target : targets) {
            services.health().setFor(actor(ctx), target, level);
        }
        return Command.SINGLE_SUCCESS;
    }
}
