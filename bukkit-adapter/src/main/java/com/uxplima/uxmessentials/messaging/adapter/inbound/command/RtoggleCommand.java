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
 * {@code /rtoggle}: flip whether the player takes part in reply routing. With it off, a {@code /reply} aimed at
 * them is declined, but a named {@code /msg} still reaches them and mail still delivers. The flip and the
 * feedback are the {@link com.uxplima.uxmessentials.messaging.application.ReplyToggle} use case's job; this
 * handler maps the invoking player. It shares the {@code uxmessentials.msg.toggle} node with {@code /msgtoggle}.
 */
@NullMarked
public final class RtoggleCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.toggle";

    public RtoggleCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rtoggle")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Toggle whether you receive replies.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender != null) {
            services.replyToggle().toggle(ref(sender));
        }
        return Command.SINGLE_SUCCESS;
    }
}
