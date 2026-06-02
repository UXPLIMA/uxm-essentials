package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /health <amount> [player]} ({@code uxmessentials.health.use}): set a player's health to a specific
 * value. The amount is bounded below by Brigadier and floored at {@code 0} in the domain ({@link HealthLevel});
 * the adapter caps it to the player's live maximum health, so a value of {@code 0} kills. Distinct from
 * {@code /heal}, which always restores to full. The {@code .others} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node; the {@code SetHealth} use case owns the effect and feedback.
 */
@NullMarked
public final class HealthCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.health.use";
    private static final int MAX_HEALTH_INPUT = 2048;

    public HealthCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("health")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, MAX_HEALTH_INPUT))
                        .executes(this::set)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(this::set)))
                .build();
    }

    @Override
    public String description() {
        return "Set a player's health.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        HealthLevel level = HealthLevel.of(ctx.getArgument("amount", Integer.class));
        services.health().setFor(ref(sender), target.get(), level);
        return Command.SINGLE_SUCCESS;
    }
}
