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
 * {@code /invsee [player]} ({@code uxmessentials.invsee.use}): open a view of another player's inventory in your
 * own screen, gated for a named target by the shared {@code uxmessentials.invsee.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node. The target
 * may be online here (a live managed copy), on another server (the command names it) or offline (read from their stored player data); the {@code OpenContainer}
 * use case owns the open and the viewer confirmation, and the adapter routes by online state.
 */
@NullMarked
public final class InvseeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = PlayerstatePermissions.INVSEE_USE;

    public InvseeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return PlayerstatePermissions.INVSEE_OTHERS;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("invsee")
                .requires(Gate.permission(PERMISSION))
                .executes(this::view)
                .then(Commands.argument("player", Args.token())
                        .suggests(onlinePlayerSuggestions())
                        .executes(this::view))
                .build();
    }

    @Override
    public String description() {
        return "View a player's inventory.";
    }

    private int view(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        resolveStorageTarget(ctx, sender, target -> services.openContainer().openInventory(viewer, target));
        return Command.SINGLE_SUCCESS;
    }
}
