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
 * {@code /god [player]} ({@code uxmessentials.god.use}): toggle damage immunity for yourself, or for another
 * player when the {@code uxmessentials.god.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node is held. The {@code ToggleGod} use case owns
 * the snapshot mutation, the reconciliation, the event, and the feedback; this handler maps the arguments and
 * resolves the optional target.
 */
@NullMarked
public final class GodCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.god.use";

    public GodCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.god.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("god")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(PlayerTargets.players("player").executes(this::toggle))
                .build();
    }

    @Override
    public String description() {
        return "Toggle damage immunity.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            services.toggleGod().toggleFor(ref(sender), target);
        }
        return Command.SINGLE_SUCCESS;
    }
}
