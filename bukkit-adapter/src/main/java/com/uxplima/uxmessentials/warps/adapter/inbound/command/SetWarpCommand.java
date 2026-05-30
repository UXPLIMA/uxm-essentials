package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setwarp <name>}: create a server-wide warp at the operator's current position, or re-anchor an
 * existing one of the same name. The create-vs-move decision is the
 * {@link com.uxplima.uxmessentials.warps.application.SetWarp} use case's job; this handler maps the name and
 * the position. The operator-only {@code uxmessentials.warp.set} node guards the command.
 */
@NullMarked
public final class SetWarpCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.set";

    public SetWarpCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setwarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Create or move a warp at your location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WarpName name = WarpName.of(ctx.getArgument("name", String.class));
        services.setWarp().set(ref(sender), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }
}
