package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /delpwarp <name>}: remove one of the player's own warps, freeing its name for reuse. The
 * {@link com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp} use case rejects a missing name
 * through the sink; this handler maps the name argument. The {@code uxmessentials.pwarp.delete} node guards
 * the command.
 */
@NullMarked
public final class DelPlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pwarp.delete";

    public DelPlayerWarpCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("delpwarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Remove one of your player warps.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        var who = ref(sender);
        PlayerWarpName name = PlayerWarpName.of(ctx.getArgument("name", String.class));
        // The delete reads then writes the database; run it off the tick thread.
        services.scheduler().async(() -> services.delPlayerWarp().delete(who, name));
        return Command.SINGLE_SUCCESS;
    }
}
