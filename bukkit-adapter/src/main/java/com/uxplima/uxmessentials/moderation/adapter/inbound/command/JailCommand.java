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
 * {@code /jail <player> <jail> [duration] [reason]}: confine a player to a named jail. With no duration the
 * jail is permanent; with one it is timed (online-only by default, wall-clock per {@code moderation.conf}).
 * The unknown-jail / exempt / duration gating and the audit line are the {@code Jail} use case's job; this
 * handler maps the name, the jail, the optional duration token and the greedy reason. The target may be
 * offline (offline jail re-applied at the next login).
 */
@NullMarked
public final class JailCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    public JailCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("jail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("jail", StringArgumentType.word())
                                .executes(ctx -> run(ctx, "", Optional.empty()))
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(ctx ->
                                                run(ctx, ctx.getArgument("duration", String.class), Optional.empty()))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> run(
                                                        ctx,
                                                        ctx.getArgument("duration", String.class),
                                                        optionalReason(ctx)))))))
                .build();
    }

    @Override
    public String description() {
        return "Confine a player to a jail, optionally for a duration.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, String duration, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String jail = ctx.getArgument("jail", String.class);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.jail().jail(actor, to, jail, duration, reason));
        return Command.SINGLE_SUCCESS;
    }
}
