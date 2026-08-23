package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;

import org.bukkit.command.CommandSender;

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
 * {@code /nightvision [player]} (alias {@code /nv}, {@code uxmessentials.nightvision.use}): toggle a permanent
 * night-vision effect. The {@code [player]} target is gated by the shared {@code uxmessentials.nightvision.others}
 * (or the cross-cutting {@code uxmessentials.playerstate.others}) node; the {@code ToggleNightVision} use case owns
 * the effect mutation and the on/off confirmation.
 */
@NullMarked
public final class NightVisionCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.nightvision.use";

    public NightVisionCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.nightvision.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("nightvision")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(PlayerTargets.players("player").executes(this::toggle))
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
        CommandSender sender = ctx.getSource().getSender();
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            services.toggleNightVision().toggleFor(actor(ctx), target);
        }
        return Command.SINGLE_SUCCESS;
    }
}
