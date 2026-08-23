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
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /foodlevel <amount> [player]} ({@code uxmessentials.foodlevel.use}): set a player's hunger to a
 * specific value. The amount is bounded {@code 0..20} by Brigadier and re-clamped in the domain
 * ({@link FoodLevel}). Distinct from {@code /feed}, which always restores to full. The {@code .others} target is
 * gated by the shared {@code uxmessentials.foodlevel.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node; the {@code SetFoodLevel} use case owns the
 * effect and feedback.
 */
@NullMarked
public final class FoodLevelCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.foodlevel.use";

    public FoodLevelCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.foodlevel.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("foodlevel")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, FoodLevel.MAX_FOOD))
                        .executes(this::set)
                        .then(PlayerTargets.players("player").executes(this::set)))
                .build();
    }

    @Override
    public String description() {
        return "Set a player's food level.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        FoodLevel food = FoodLevel.of(ctx.getArgument("amount", Integer.class));
        for (PlayerRef target : targets) {
            services.foodLevel().setFor(actor(ctx), target, food);
        }
        return Command.SINGLE_SUCCESS;
    }
}
