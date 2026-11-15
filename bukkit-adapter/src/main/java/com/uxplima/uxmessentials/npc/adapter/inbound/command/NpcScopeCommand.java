package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /npc scope <name> <server|server:<id>|set:<name>|clear>}: choose which servers of the network show an NPC.
 * The write reaches the shared database, so it runs on the async lane and the reply follows from there.
 */
@NullMarked
final class NpcScopeCommand extends NpcCommandSupport {

    private static final String USAGE = "<name> <server|server:id|set:name|clear>";

    private final Scheduler scheduler;

    NpcScopeCommand(
            NpcServices services,
            Supplier<? extends Collection<String>> npcNames,
            Messages messages,
            Scheduler scheduler) {
        super(services, npcNames, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The {@code scope} node the {@code /npc} literal attaches. */
    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("scope")
                .executes(ctx -> usage(ctx, "npc scope", USAGE, "Choose the servers that show an NPC"))
                .then(nameArgument()
                        .executes(ctx -> usage(ctx, "npc scope", USAGE, "Choose the servers that show an NPC"))
                        .then(Commands.argument("scope", Args.token())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.scope().suggestions()))
                                .executes(this::scope)));
    }

    private int scope(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        NpcName name = nameArg(ctx);
        String scope = ctx.getArgument("scope", String.class);
        CommandSender sender = Sender.audience(ctx.getSource());
        scheduler.async(() -> {
            try {
                services.scope().set(actor, name, scope);
            } catch (RuntimeException failed) {
                // A failed write tells the operator rather than ending in silence, and still reaches the error log.
                feedback.send(sender, SharedMessageKey.COMMAND_ERROR, Map.of());
                throw failed;
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
