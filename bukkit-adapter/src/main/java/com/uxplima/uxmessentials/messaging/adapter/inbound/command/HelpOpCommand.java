package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /helpop <text>}: open a player→staff support request. The mute gate and the fan-out to staff holding
 * the receive node are the {@link com.uxplima.uxmessentials.messaging.application.HelpOp} use case's job; this
 * handler maps the greedy request text.
 */
@NullMarked
public final class HelpOpCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.helpop.use";

    public HelpOpCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("helpop")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Open a staff support request.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player requester = player(ctx);
        if (requester == null) {
            return 0;
        }
        MessageBody text = body(requester, ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        services.helpOp().raise(ref(requester), text);
        return Command.SINGLE_SUCCESS;
    }
}
