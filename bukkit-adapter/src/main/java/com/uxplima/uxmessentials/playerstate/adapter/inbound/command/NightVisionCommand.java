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
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /nightvision} (alias {@code /nv}, {@code uxmessentials.nightvision.use}): toggle a permanent
 * night-vision effect on yourself. Self-only. The {@code ToggleNightVision} use case owns the effect mutation
 * and the on/off confirmation.
 */
@NullMarked
public final class NightVisionCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.nightvision.use";

    public NightVisionCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("nightvision")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("nv");
    }

    @Override
    public String description() {
        return "Toggle night vision.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.toggleNightVision().toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
