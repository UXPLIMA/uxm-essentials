package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /hologram scope <name> <server|server:<id>|set:<name>|clear>}: choose which servers of the network show a
 * hologram. The write reaches the shared database, so it runs on the async lane and the reply follows from there.
 */
@NullMarked
final class HologramScopeCommand extends HologramCommandSupport {

    private static final String USAGE = "<name> <server|server:id|set:name|clear>";

    private final Scheduler scheduler;

    HologramScopeCommand(
            HologramServices services,
            Messages messages,
            Supplier<? extends Collection<String>> hologramNames,
            Scheduler scheduler) {
        super(services, messages, hologramNames);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The {@code scope} node the {@code /hologram} literal attaches. */
    List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(Commands.literal("scope")
                .executes(ctx -> usage(ctx, "hologram scope", USAGE, "Choose the servers that show a hologram"))
                .then(nameArgument("name")
                        .executes(ctx -> usage(ctx, "hologram scope", USAGE, "Choose the servers that show a hologram"))
                        .then(Commands.argument("scope", Args.token())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.scope().suggestions()))
                                .executes(this::scope))));
    }

    private int scope(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        HologramName name = HologramName.of(ctx.getArgument("name", String.class));
        String scope = ctx.getArgument("scope", String.class);
        scheduler.async(() -> services.scope().set(actor, name, scope));
        return Command.SINGLE_SUCCESS;
    }
}
