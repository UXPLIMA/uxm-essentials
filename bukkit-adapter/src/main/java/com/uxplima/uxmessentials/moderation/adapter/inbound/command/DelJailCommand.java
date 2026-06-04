package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

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
import org.jspecify.annotations.NullMarked;

/**
 * {@code /deljail <name>}: remove a DB-backed jail location, freeing its name. The not-found feedback and the
 * confirmation are the {@link com.uxplima.uxmessentials.moderation.application.DelJail} use case's job; this
 * handler maps the name argument. Only a stored jail can be removed this way — a config-defined jail name lives
 * in {@code moderation.conf}, not the store. The command reuses the shared {@code uxmessentials.moderation.jail}
 * node, and (unlike {@code /setjail}) a console source is allowed since no position is captured.
 */
@NullMarked
public final class DelJailCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    public DelJailCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("deljail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(
                                () -> services.listJails().suggestionNames()))
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Remove a defined jail.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        services.delJail().delete(actor(ctx), ctx.getArgument("name", String.class));
        return Command.SINGLE_SUCCESS;
    }
}
