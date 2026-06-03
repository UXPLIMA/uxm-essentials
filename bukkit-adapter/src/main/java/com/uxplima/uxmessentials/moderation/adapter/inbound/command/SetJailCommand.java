package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import org.bukkit.entity.Player;

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
 * {@code /setjail <name>}: save the staff member's current position as a named jail in the DB-backed store, or
 * re-anchor an existing one of the same name. The upsert and the confirmation are the
 * {@link com.uxplima.uxmessentials.moderation.application.SetJail} use case's job; this handler maps the name
 * and the position. The command reuses the shared {@code uxmessentials.moderation.jail} node (matching
 * {@code /jails} and {@code /jailedplayers}), and a console source is rejected — the position is the sender's.
 */
@NullMarked
public final class SetJailCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    public SetJailCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setjail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("name", StringArgumentType.word()).executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Define a jail at your location.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("name", String.class);
        services.setJail().set(actor(ctx), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }
}
