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
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ice [player] [seconds]} ({@code uxmessentials.ice.use}): apply the powder-snow freezing effect to a
 * player for a number of seconds — the cosmetic opposite of {@code /burn}. The seconds argument is optional
 * (defaulting to {@link #DEFAULT_SECONDS}), bounded by Brigadier and clamped to a sane range in the domain
 * ({@link FreezeDuration}). The {@code [player]} target is gated by the shared
 * {@code uxmessentials.ice.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node; the {@code Freeze} use case owns the effect and feedback.
 */
@NullMarked
public final class IceCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.ice.use";
    private static final int DEFAULT_SECONDS = 7;

    public IceCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.ice.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ice")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::ice)
                .then(PlayerTargets.players("player")
                        .executes(this::ice)
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, FreezeDuration.MAX_SECONDS))
                                .executes(this::ice)))
                .build();
    }

    @Override
    public String description() {
        return "Freeze a player for some seconds.";
    }

    private int ice(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        FreezeDuration duration = FreezeDuration.ofSeconds(seconds(ctx));
        for (PlayerRef target : targets) {
            services.freeze().freezeFor(ref(sender), target, duration);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int seconds(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "seconds");
        } catch (IllegalArgumentException absent) {
            return DEFAULT_SECONDS;
        }
    }
}
