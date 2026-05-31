package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

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
import org.jspecify.annotations.NullMarked;

/**
 * {@code /unbanip <ip>}: lift an IP ban. Shares the {@code moderation.banip} node; the not-banned gate and the
 * audit line are the {@code UnbanIp} use case's job.
 */
@NullMarked
public final class UnbanipCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.banip";

    public UnbanipCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("unbanip")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("ip", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Lift an IP ban.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        services.unbanIp().unbanIp(actor(ctx), ctx.getArgument("ip", String.class));
        return Command.SINGLE_SUCCESS;
    }
}
