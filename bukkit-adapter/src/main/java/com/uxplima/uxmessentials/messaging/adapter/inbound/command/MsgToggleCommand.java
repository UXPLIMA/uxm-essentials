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
 * {@code /msgtoggle}: flip whether the player accepts incoming private messages. With DMs off a {@code /msg}
 * to them is refused, but mail still delivers. The flip and the feedback are the
 * {@link com.uxplima.uxmessentials.messaging.application.MsgToggle} use case's job; this handler maps the
 * invoking player.
 */
@NullMarked
public final class MsgToggleCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.toggle";

    public MsgToggleCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("msgtoggle")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Refuse or accept incoming private messages.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender != null) {
            services.msgToggle().toggle(ref(sender));
        }
        return Command.SINGLE_SUCCESS;
    }
}
