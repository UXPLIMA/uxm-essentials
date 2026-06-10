package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ice [player] [seconds]} ({@code uxmessentials.ice.use}): apply the powder-snow freezing effect to a
 * player for a number of seconds — the cosmetic opposite of {@code /burn}. The seconds argument is optional
 * (defaulting to {@link #DEFAULT_SECONDS}), bounded by Brigadier and clamped to a sane range in the domain
 * ({@link FreezeDuration}). The {@code [player]} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node; the {@code Freeze} use case owns the effect and feedback.
 */
@NullMarked
public final class IceCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.ice.use";
    private static final int DEFAULT_SECONDS = 7;

    public IceCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ice")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::ice)
                .then(Commands.argument("player", ArgumentTypes.player())
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
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.freeze().freezeFor(ref(sender), target.get(), FreezeDuration.ofSeconds(seconds(ctx)));
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
