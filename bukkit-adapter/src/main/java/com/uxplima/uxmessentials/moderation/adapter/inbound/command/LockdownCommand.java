package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /lockdown [on|off]}: flip the server-wide login gate. Bare {@code /lockdown} toggles the current
 * state; {@code /lockdown on}/{@code off} sets it explicitly so a script never depends on the prior state. The
 * flag flip, the actor confirmation and the staff announcement are the {@code Lockdown} use case's job; this
 * handler only maps the optional argument and hops the work off the tick thread, since both reading the current
 * state (for a toggle) and persisting the new one are DB writes that must not block the command.
 */
@NullMarked
public final class LockdownCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.lockdown";
    private static final String STATE_ARG = "state";
    private static final String ON = "on";
    private static final String OFF = "off";

    private final Scheduler scheduler;

    public LockdownCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("lockdown")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument(STATE_ARG, StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() -> List.of(ON, OFF)))
                        .executes(ctx -> run(ctx, Optional.of(ctx.getArgument(STATE_ARG, String.class)))))
                .build();
    }

    @Override
    public String description() {
        return "Lock the server so only bypass holders can join.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> state) {
        PlayerRef actor = actor(ctx);
        scheduler.async(() -> apply(actor, state));
        return Command.SINGLE_SUCCESS;
    }

    private void apply(PlayerRef actor, Optional<String> state) {
        boolean target = state.map(LockdownCommand::parse)
                .orElseGet(() -> !services.lockdown().isLockedDown());
        services.lockdown().setLockdown(actor, target);
    }

    /** An explicit {@code on}/{@code off} token maps to true/false; any other word is treated as {@code off}. */
    private static boolean parse(String state) {
        return state.equalsIgnoreCase(ON);
    }
}
