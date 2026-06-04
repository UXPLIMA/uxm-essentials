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
 * {@code /heal [player]} ({@code uxmessentials.heal.use}): restore health (and optionally clear effects) for
 * yourself or another player with the {@code uxmessentials.playerstate.others} node. The {@code Heal} use case
 * owns the apply-once effect, the event, and the feedback.
 */
@NullMarked
public final class HealCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.heal.use";

    public HealCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("heal")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::heal)
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::heal))
                .build();
    }

    @Override
    public String description() {
        return "Restore health.";
    }

    private int heal(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.heal().healFor(ref(sender), target.get());
        return Command.SINGLE_SUCCESS;
    }
}
