package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /endersee [player]} ({@code uxmessentials.endersee.use}): open a view of another player's ender chest in
 * your own screen, gated for a named target by the shared {@code uxmessentials.endersee.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node. The
 * target may be online here (the live ender chest), on another server (the command names it) or offline (read from their stored player data); the
 * {@code OpenContainer} use case owns the open and the viewer confirmation, and the adapter routes by online
 * state.
 */
@NullMarked
public final class EnderseeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = PlayerstatePermissions.ENDERSEE_USE;

    public EnderseeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return PlayerstatePermissions.ENDERSEE_OTHERS;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("endersee")
                .requires(Gate.permission(PERMISSION))
                .executes(this::view)
                .then(Commands.argument("player", Args.token())
                        .suggests(onlinePlayerSuggestions())
                        .executes(this::view))
                .build();
    }

    @Override
    public String description() {
        return "View a player's ender chest.";
    }

    private int view(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        resolveStorageTarget(ctx, sender, target -> services.openContainer().openEnderChest(viewer, target));
        return Command.SINGLE_SUCCESS;
    }
}
