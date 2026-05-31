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
 * {@code /socialspy}: flip whether a staff member observes other players' private messages (audit-relevant).
 * While on, each delivered message is fanned out to this observer by the send path unless they are a party to
 * it. The flip and the feedback are the
 * {@link com.uxplima.uxmessentials.messaging.application.SocialSpy} use case's job; this handler maps the
 * invoking staff member.
 */
@NullMarked
public final class SocialSpyCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.socialspy";

    public SocialSpyCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("socialspy")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Observe other players' private messages.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player staff = player(ctx);
        if (staff != null) {
            services.socialSpy().toggle(ref(staff));
        }
        return Command.SINGLE_SUCCESS;
    }
}
