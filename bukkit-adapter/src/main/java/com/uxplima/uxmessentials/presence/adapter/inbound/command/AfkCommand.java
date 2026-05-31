package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /afk [reason]} ({@code uxmessentials.afk.use}): toggle your own AFK state, optionally with a greedy
 * reason. The {@code MarkAfk} use case owns the toggle, the away/back event, the broadcast, and the feedback;
 * this handler only maps the optional reason and the invoking player. Auto-AFK on idle is the sweep's job and
 * needs no command.
 */
@NullMarked
public final class AfkCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.afk.use";

    public AfkCommand(PresenceServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("afk")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> toggle(ctx, Optional.empty()))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> toggle(ctx, Optional.of(ctx.getArgument("reason", String.class)))))
                .build();
    }

    @Override
    public String description() {
        return "Toggle your AFK state.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.markAfk().toggle(ref(sender), reason);
        return Command.SINGLE_SUCCESS;
    }
}
