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
 * {@code /warpinfo <name>}: show a warp's owner, creation time, cost, and required permission. The
 * {@link com.uxplima.uxmessentials.warps.application.WarpInfo} use case reads the warp and pushes the detail
 * lines through the sink (rejecting a missing name); this handler maps the name argument.
 */
@NullMarked
public final class WarpInfoCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.info";

    public WarpInfoCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("warpinfo")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(warpNameArgument().executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Show a warp's owner, creation time and cost.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.warpInfo().show(ref(sender), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
