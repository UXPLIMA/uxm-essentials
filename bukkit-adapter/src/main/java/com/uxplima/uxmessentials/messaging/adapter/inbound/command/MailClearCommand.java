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
 * {@code /mailclear}: the standalone alias for {@code /mail clear}, emptying the owner's persistent mailbox.
 * It shares the same {@link com.uxplima.uxmessentials.messaging.application.ClearMail} use case as the
 * {@code clear} sub-command so the empty-box feedback and the cleared confirmation are identical.
 */
@NullMarked
public final class MailClearCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.mail.use";

    public MailClearCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mailclear")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Clear your mailbox.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player owner = player(ctx);
        if (owner != null) {
            services.clearMail().clear(ref(owner));
        }
        return Command.SINGLE_SUCCESS;
    }
}
