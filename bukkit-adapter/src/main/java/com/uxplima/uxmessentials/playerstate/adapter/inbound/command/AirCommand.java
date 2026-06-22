package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /air <seconds> [player]} ({@code uxmessentials.air.use}): set a player's remaining air. The seconds
 * argument is bounded non-negative by Brigadier and converted to ticks, then clamped to the player's maximum
 * air in the domain ({@link AirAmount}) by the adapter. The {@code .others} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node; the {@code SetAir} use case owns the effect and feedback.
 */
@NullMarked
public final class AirCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.air.use";

    public AirCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("air")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(this::set)
                        .then(PlayerTargets.players("player").executes(this::set)))
                .build();
    }

    @Override
    public String description() {
        return "Set a player's remaining air.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        AirAmount air = AirAmount.ofSeconds(ctx.getArgument("seconds", Integer.class), Integer.MAX_VALUE);
        for (PlayerRef target : targets) {
            services.air().setFor(ref(sender), target, air);
        }
        return Command.SINGLE_SUCCESS;
    }
}
