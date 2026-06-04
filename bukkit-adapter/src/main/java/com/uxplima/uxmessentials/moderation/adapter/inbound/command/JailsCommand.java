package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /jails}: list the jails available to {@code /jail <player> <jail>} — the named jails configured in
 * {@code moderation.conf} merged with the DB-backed jails created through {@code /setjail}. A read-only
 * companion that reuses the jail permission since anyone who may jail needs to know which names are valid. The
 * {@code ListJails} use case does the rendering; this node maps the bare invocation and, like its jail
 * siblings, runs the use case off the tick thread through the {@link Scheduler} port since the directory
 * query touches the DB. The use case's notifier hops each reply back to the actor's region thread.
 */
@NullMarked
public final class JailsCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    private final Scheduler scheduler;

    public JailsCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
        PlayerRef actor = actor(ctx);
        scheduler.async(() -> services.listJails().list(actor));
        return Command.SINGLE_SUCCESS;
    }
}
