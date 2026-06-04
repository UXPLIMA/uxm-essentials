package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /movewarp <name>}: re-anchor an existing warp to the operator's current position, keeping its name
 * and settings. The {@link com.uxplima.uxmessentials.warps.application.MoveWarp} use case rejects a missing
 * name through the sink; this handler maps the name and the current position. The operator-only
 * {@code uxmessentials.warp.move} node guards the command.
 */
@NullMarked
public final class MoveWarpCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.move";

    public MoveWarpCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("movewarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(warpNameArgument().executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Move a warp to your current location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WarpName name = WarpName.of(ctx.getArgument("name", String.class));
        services.moveWarp().move(ref(sender), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }
}
