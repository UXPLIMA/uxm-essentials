package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ignorelist}: show the player the persistent ignore list {@code /ignore} and {@code /unignore} build.
 * It takes no argument and reuses the {@code uxmessentials.msg.ignore} node, since seeing your own list is part
 * of managing it; the listing itself — empty notice, header with count, one line per ignored player — is the
 * {@link com.uxplima.uxmessentials.messaging.application.ListIgnores} use case's job.
 */
@NullMarked
public final class IgnoreListCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.ignore";

    public IgnoreListCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ignorelist")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List the players you are ignoring.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listIgnores().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
