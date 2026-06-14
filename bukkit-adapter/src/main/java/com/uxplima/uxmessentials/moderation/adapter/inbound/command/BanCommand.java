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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ban <player> [-s] [reason]}: a permanent UUID ban. Unlike {@code /tempban} there is no duration
 * argument — the {@code Ban} use case records a far-future tempban row, kicks an online target immediately and
 * the login listener bars reconnection. This handler maps the name and the greedy reason; a leading {@code -s}
 * in the reason suppresses the staff broadcast. An exempt target or an unknown name is reported by the use case
 * and the resolver respectively.
 */
@NullMarked
public final class BanCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.ban";

    private final boolean silentByDefault;

    public BanCommand(ModerationServices services, Messages messages, MessageSink sink, boolean silentByDefault) {
        super(services, messages, sink);
        this.silentByDefault = silentByDefault;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ban")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, Optional.empty()))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, optionalReason(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Permanently ban a player (prefix the reason with -s to ban silently).";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        SilentReason parsed = silentReason(reason, silentByDefault);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.ban().ban(actor, to, parsed.reason(), parsed.silent()));
        return Command.SINGLE_SUCCESS;
    }
}
