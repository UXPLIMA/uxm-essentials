package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;

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
                .then(PlayerTargets.players("player").executes(this::heal))
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
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            services.heal().healFor(ref(sender), target);
        }
        return Command.SINGLE_SUCCESS;
    }
}
