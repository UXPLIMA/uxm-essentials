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
 * {@code /ban <player> [reason]}: a permanent UUID ban. Unlike {@code /tempban} there is no duration argument
 * — the {@code Ban} use case records a far-future tempban row, kicks an online target immediately and the
 * login listener bars reconnection. This handler maps the name and the greedy reason; an exempt target or an
 * unknown name is reported by the use case and the resolver respectively.
 */
@NullMarked
public final class BanCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.ban";

    public BanCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ban")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.empty()))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, optionalReason(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Permanently ban a player.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.ban().ban(actor, to, reason));
        return Command.SINGLE_SUCCESS;
    }
}
