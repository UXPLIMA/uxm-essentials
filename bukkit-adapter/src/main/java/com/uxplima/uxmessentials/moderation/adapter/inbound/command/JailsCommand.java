package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /jails}: list the named jails configured in {@code moderation.conf}. A read-only companion to
 * {@code /jail <player> <jail>} — it reuses the jail permission since anyone who may jail needs to know which
 * names are valid. The {@code ListJails} use case does the rendering; this node only maps the bare invocation.
 */
@NullMarked
public final class JailsCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    public JailsCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("jails")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List the configured jails.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        services.listJails().list(actor(ctx));
        return Command.SINGLE_SUCCESS;
    }
}
