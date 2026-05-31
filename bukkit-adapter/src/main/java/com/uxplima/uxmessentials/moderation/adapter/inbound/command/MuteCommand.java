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
 * {@code /mute <player> [duration] [reason]}: gag a player's outbound messaging. With no duration the mute is
 * permanent; with one ({@code /tempmute} is the explicit-duration alias) it expires. The exempt/duration
 * gating and the audit line are the {@code Mute} use case's job; this handler maps the name, the optional
 * duration token, and the greedy reason.
 */
@NullMarked
public final class MuteCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.mute";

    public MuteCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mute")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> run(ctx, "", Optional.empty()))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> run(ctx, ctx.getArgument("duration", String.class), Optional.empty()))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> run(
                                                ctx, ctx.getArgument("duration", String.class), optionalReason(ctx))))))
                .build();
    }

    @Override
    public String description() {
        return "Mute a player, optionally for a duration.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, String duration, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.mute().mute(actor, to, duration, reason));
        return Command.SINGLE_SUCCESS;
    }
}
