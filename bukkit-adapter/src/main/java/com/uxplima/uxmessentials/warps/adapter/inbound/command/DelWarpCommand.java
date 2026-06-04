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
 * {@code /delwarp <name>}: remove a server-wide warp, freeing its name for reuse. The
 * {@link com.uxplima.uxmessentials.warps.application.DelWarp} use case rejects a missing name through the
 * sink; this handler maps the name argument. The operator-only {@code uxmessentials.warp.delete} node guards
 * the command.
 */
@NullMarked
public final class DelWarpCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.delete";

    public DelWarpCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("delwarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(warpNameArgument().executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Remove a server warp.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.delWarp().delete(ref(sender), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
