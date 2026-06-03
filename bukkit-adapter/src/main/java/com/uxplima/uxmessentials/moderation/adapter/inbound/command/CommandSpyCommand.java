package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /commandspy} (alias {@code /cspy}): flip whether a staff member watches the commands other players
 * run (audit-relevant). While on, the command-watch listener fans out each player's command to this observer.
 * The flip and the feedback are the {@link com.uxplima.uxmessentials.moderation.application.CommandSpy} use
 * case's job; this handler maps the invoking staff member and is the moderation sibling of {@code /socialspy}.
 */
@NullMarked
public final class CommandSpyCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.commandspy";

    public CommandSpyCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("commandspy")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Watch the commands other players run.";
    }

    @Override
    public List<String> aliases() {
        return List.of("cspy");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef staff = actor(ctx);
        services.commandSpy().toggle(staff);
        return Command.SINGLE_SUCCESS;
    }
}
