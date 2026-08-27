package com.uxplima.uxmessentials.customcommands.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsMessageKey;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.RunOutcome;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandDuration;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The operator surface over the loaded definitions: what is loaded, what one definition says, re-reading the folder
 * or a single file, running a chain for somebody, and asking which gate would stop you.
 *
 * <p>It reads the live catalog through the reference the wiring swaps on reload, so a reloaded definition takes
 * effect for {@code list}, {@code info}, {@code run} and {@code test} without rebuilding anything. Re-reading the
 * folder is disk work, so it goes off the tick thread and reports when it lands.
 */
public final class CustomCommandCommand implements CommandRegistration {

    /** The node gating the whole surface. */
    private static final String ADMIN = "uxmessentials.customcommand.admin";

    /** The node needed to run a chain for somebody other than yourself. */
    private static final String RUN_OTHERS = "uxmessentials.customcommand.run.others";

    private final AtomicReference<CustomCommandLoader.LoadResult> state;
    private final Supplier<CustomCommandLoader.LoadResult> reloadAll;
    private final Function<String, CustomCommandLoader.LoadResult> reloadOne;
    private final RunCustomCommand runner;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    public CustomCommandCommand(
            AtomicReference<CustomCommandLoader.LoadResult> state,
            Supplier<CustomCommandLoader.LoadResult> reloadAll,
            Function<String, CustomCommandLoader.LoadResult> reloadOne,
            RunCustomCommand runner,
            Scheduler scheduler,
            Messages messages) {
        this.state = Objects.requireNonNull(state, "state");
        this.reloadAll = Objects.requireNonNull(reloadAll, "reloadAll");
        this.reloadOne = Objects.requireNonNull(reloadOne, "reloadOne");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("customcmd")
                .requires(src -> src.getSender().hasPermission(ADMIN))
                .executes(this::usage)
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("info").then(idArgument().executes(this::info)))
                .then(Commands.literal("reload")
                        .executes(this::reloadAll)
                        .then(idArgument().executes(this::reloadOne)))
                .then(Commands.literal("test").then(idArgument().executes(this::test)))
                .then(Commands.literal("run")
                        .then(idArgument()
                                .executes(this::runForSelf)
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(this::runForOther))))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("customcommand");
    }

    @Override
    public String description() {
        return "Manage operator-defined custom commands";
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests(
                        CommandSuggestions.fromStrings(() -> loaded().catalog().ids()));
    }

    private int usage(CommandContext<CommandSourceStack> ctx) {
        feedback.send(ctx.getSource().getSender(), CustomCommandsMessageKey.CUSTOMCOMMAND_USAGE);
        return Command.SINGLE_SUCCESS;
    }

    /** The catalog as it stands right now; the reference is swapped whole by a reload, so a read is never torn. */
    private CustomCommandLoader.LoadResult loaded() {
        return Objects.requireNonNull(state.get(), "state");
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        CustomCommandLoader.LoadResult loaded = loaded();
        List<CustomCommand> commands = loaded.catalog().commands();
        if (commands.isEmpty()) {
            feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_LIST_EMPTY);
        } else {
            feedback.send(
                    sender,
                    CustomCommandsMessageKey.CUSTOMCOMMAND_LIST_HEADER,
                    Map.of("count", String.valueOf(commands.size())));
            for (CustomCommand command : commands) {
                feedback.send(
                        sender,
                        CustomCommandsMessageKey.CUSTOMCOMMAND_LIST_ENTRY,
                        Map.of(
                                "id", command.id().value(),
                                "name", command.literal().name(),
                                "aliases", String.join(", ", command.literal().aliases())));
            }
        }
        for (String warning : loaded.warnings()) {
            feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_LIST_WARNING, Map.of("warning", warning));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "id");
        Optional<CustomCommand> found = loaded().catalog().byId(id);
        if (found.isEmpty()) {
            return notFound(sender, id);
        }
        CustomCommand command = found.get();
        feedback.send(
                sender,
                CustomCommandsMessageKey.CUSTOMCOMMAND_INFO_HEADER,
                Map.of(
                        "id", command.id().value(),
                        "name", command.literal().name(),
                        "aliases", String.join(", ", command.literal().aliases())));
        feedback.send(
                sender,
                CustomCommandsMessageKey.CUSTOMCOMMAND_INFO_GATES,
                Map.of(
                        "permission", command.permission().orElse("-"),
                        "console", String.valueOf(command.consoleAllowed()),
                        "cooldown", CommandDuration.format(command.cooldown()),
                        "warmup", CommandDuration.format(command.warmup()),
                        "cost", String.valueOf(command.cost())));
        for (CommandArgument argument : command.arguments()) {
            feedback.send(
                    sender,
                    CustomCommandsMessageKey.CUSTOMCOMMAND_INFO_ARGUMENT,
                    Map.of(
                            "name", argument.name(),
                            "type", argument.rest() ? "rest" : argument.kind().token(),
                            "required", String.valueOf(!argument.optional())));
        }
        feedback.send(
                sender,
                CustomCommandsMessageKey.CUSTOMCOMMAND_INFO_CHAIN,
                Map.of(
                        "requirements", String.valueOf(command.requirements().size()),
                        "actions", String.valueOf(command.actions().steps().size())));
        return Command.SINGLE_SUCCESS;
    }

    private int reloadAll(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        scheduler.async(() -> {
            CustomCommandLoader.LoadResult loaded = reloadAll.get();
            state.set(loaded);
            scheduler.onGlobal(() -> feedback.send(
                    sender,
                    CustomCommandsMessageKey.CUSTOMCOMMAND_RELOADED,
                    Map.of(
                            "loaded", String.valueOf(loaded.catalog().commands().size()),
                            "skipped", String.valueOf(loaded.skipped().size()))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int reloadOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "id");
        scheduler.async(() -> {
            CustomCommandLoader.LoadResult loaded = reloadOne.apply(id);
            state.set(loaded);
            scheduler.onGlobal(() -> feedback.send(
                    sender,
                    CustomCommandsMessageKey.CUSTOMCOMMAND_RELOADED_ONE,
                    Map.of(
                            "id", id,
                            "loaded", String.valueOf(loaded.catalog().commands().size()),
                            "skipped", String.valueOf(loaded.skipped().size()))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int test(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "id");
        Optional<CustomCommand> found = loaded().catalog().byId(id);
        if (found.isEmpty()) {
            return notFound(sender, id);
        }
        RunOutcome outcome = runner.dryRun(found.get(), CommandFeedback.refOf(sender), !(sender instanceof Player));
        if (outcome instanceof RunOutcome.Ok) {
            feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_TEST_PASSED);
        } else {
            feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_TEST_BLOCKED, Map.of("gate", outcome.gate()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runForSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        return dispatch(ctx, sender, CommandFeedback.refOf(sender), !(sender instanceof Player));
    }

    private int runForOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        if (resolved.isEmpty()) {
            feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_NOT_FOUND, Map.of("id", "player"));
            return 0;
        }
        Player target = resolved.get(0);
        if (!target.equals(sender) && !sender.hasPermission(RUN_OTHERS)) {
            feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_RUN_OTHERS_DENIED);
            return 0;
        }
        return dispatch(ctx, sender, new PlayerRef(target.getUniqueId(), target.getName()), false);
    }

    /** Run the named definition for {@code actor} and tell the sender it went out. */
    private int dispatch(
            CommandContext<CommandSourceStack> ctx, CommandSender sender, PlayerRef actor, boolean console) {
        String id = StringArgumentType.getString(ctx, "id");
        Optional<CustomCommand> found = loaded().catalog().byId(id);
        if (found.isEmpty()) {
            return notFound(sender, id);
        }
        runner.run(found.get(), actor, console, Map.of("args", ""));
        feedback.send(
                sender,
                CustomCommandsMessageKey.CUSTOMCOMMAND_RUN_DISPATCHED,
                Map.of("id", id, "player", actor.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int notFound(CommandSender sender, String id) {
        feedback.send(sender, CustomCommandsMessageKey.CUSTOMCOMMAND_NOT_FOUND, Map.of("id", id));
        return 0;
    }
}
