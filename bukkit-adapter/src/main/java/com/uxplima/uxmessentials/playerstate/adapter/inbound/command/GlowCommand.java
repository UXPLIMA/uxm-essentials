package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /glow} ({@code uxmessentials.glow.use}): toggle a permanent glowing outline on yourself. Self-only.
 * The {@code ToggleGlow} use case owns the outline mutation and the on/off confirmation.
 */
@NullMarked
public final class GlowCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.glow.use";

    public GlowCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("glow")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public String description() {
        return "Toggle a glowing outline.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.toggleGlow().toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
