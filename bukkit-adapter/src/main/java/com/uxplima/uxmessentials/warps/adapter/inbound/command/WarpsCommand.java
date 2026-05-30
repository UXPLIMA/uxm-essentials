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
import org.jspecify.annotations.NullMarked;

/**
 * {@code /warps}: list the warps the player may use. The
 * {@link com.uxplima.uxmessentials.warps.application.ListWarps} use case filters to the warps whose gates
 * the player holds and pushes the header, the per-entry lines, or the empty notice through the sink; this
 * handler maps the source to the player.
 */
@NullMarked
public final class WarpsCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.list";

    public WarpsCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("warps")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List warps you may use.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listWarps().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
