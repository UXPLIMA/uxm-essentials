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
 * {@code /jailedplayers}: list the players currently jailed — the companion of {@code /banlist} and
 * {@code /mutelist}. The {@code ListJailed} use case runs the bounded DB query and renders the page, hopped
 * off the tick thread through the {@link Scheduler} port so a large jail table never blocks the command. It
 * shares the {@code uxmessentials.moderation.jail} node with {@code /jail} and {@code /jails}.
 */
@NullMarked
public final class JailedPlayersCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    private final Scheduler scheduler;

    public JailedPlayersCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("jailedplayers")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List the players currently jailed.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        scheduler.async(() -> services.listJailed().list(actor));
        return Command.SINGLE_SUCCESS;
    }
}
