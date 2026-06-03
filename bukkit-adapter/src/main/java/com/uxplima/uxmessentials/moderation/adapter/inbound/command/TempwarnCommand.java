package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tempwarn <player> <duration> [reason]}: append a warning that lapses on its own — the timed sibling
 * of {@code /warn}. The duration is mandatory (a permanent warning is {@code /warn}); the {@code TempWarn} use
 * case applies the exempt gate, the duration parse and the audit line, and the target may be offline. It
 * shares the {@code uxmessentials.moderation.warn} node. This handler maps the name, the duration and the
 * greedy reason.
 */
@NullMarked
public final class TempwarnCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.warn";

    public TempwarnCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tempwarn")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> run(ctx, Optional.empty()))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, optionalReason(ctx))))))
                .build();
    }

    @Override
    public String description() {
        return "Warn a player for a duration.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String duration = ctx.getArgument("duration", String.class);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.tempWarn().warn(actor, to, duration, reason));
        return Command.SINGLE_SUCCESS;
    }
}
