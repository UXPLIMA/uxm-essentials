package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /warp <name> [player]}: teleport to a server warp. With no trailing player the sender warps
 * themselves; with one, and the {@code uxmessentials.warp.others} node, staff send that player to the warp
 * instead. The per-warp permission gate, the optional cost charge, and the delegated gated hop are the
 * {@link com.uxplima.uxmessentials.warps.application.UseWarp} use case's job (the gate applies to whoever is
 * being sent); this handler maps the arguments and resolves the optional target. The base
 * {@code uxmessentials.warp.use} node guards the command; the per-destination
 * {@code uxmessentials.warp.use.<warp>} node is checked inside the use case.
 */
@NullMarked
public final class WarpCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.warp.others";

    public WarpCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("warp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(this::run)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission(OTHERS_PERMISSION))
                                .executes(this::sendOther)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to a server warp.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.useWarp().use(ref(sender), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int sendOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String targetName = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(targetName);
        if (target.isEmpty()) {
            unknownPlayer(ctx.getSource().getSender(), targetName);
            return 0;
        }
        services.useWarp().useFor(ref(sender), target.get(), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }
}
