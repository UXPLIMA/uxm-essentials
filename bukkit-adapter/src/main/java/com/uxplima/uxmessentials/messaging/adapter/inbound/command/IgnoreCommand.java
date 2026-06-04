package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ignore <player>}: add a player to the owner's persistent ignore list — the verb form of the ignore
 * behaviour. The target is resolved by name (online resolution suffices for the verb); the self-check, the
 * idempotent store write, and the feedback are the
 * {@link com.uxplima.uxmessentials.messaging.application.Ignore} use case's job.
 */
@NullMarked
public final class IgnoreCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.ignore";

    public IgnoreCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ignore")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Ignore a player's messages.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef owner = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            notify(owner, UNKNOWN_PLAYER, Map.of("player", name));
            return 0;
        }
        services.ignore().ignore(owner, target.get());
        return Command.SINGLE_SUCCESS;
    }
}
