package com.uxplima.uxmessentials.customcommands.adapter.inbound.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.RunOutcome;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentNodes;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;

/**
 * One operator-defined command as a Brigadier registration. It is a {@link CommandRegistration} like any built-in
 * command, which is the whole point: the bootstrap collects it before the command catalog resolves, so a custom
 * command can be renamed, realiased, given per-locale aliases or disabled from {@code commands.conf} exactly as
 * {@code /home} can. Its {@link #commandId()} is namespaced ({@code custom:<id>}) so an operator-authored id can
 * never silently claim a built-in command's catalog key.
 *
 * <p>The node's shape comes from the definition: the permission gates visibility through {@code requires}, the
 * declared arguments become the shared {@link ArgumentNodes} chain, and the executor hands the parsed values to
 * {@link RunCustomCommand}. Every gate decision, and every message, belongs to the use case; this class only
 * translates between Brigadier and the domain.
 *
 * <p>The node is built once from the definition that was on disk at startup, because Brigadier only accepts
 * registrations while the server starts. What the node <em>runs</em> is looked up fresh on every invocation, so a
 * reload that rewrites a command's gates, requirements or actions takes effect immediately; only its word, aliases
 * and argument shape wait for the next restart.
 */
public final class CustomCommandRegistration implements CommandRegistration {

    /** The catalog key prefix that keeps an operator id out of the built-in key space. */
    private static final String KEY_PREFIX = "custom:";

    /** The argument key carrying the raw remaining input, which an action reaches as {@code %args%}. */
    private static final String RAW_ARGUMENTS = "args";

    private final CustomCommand command;
    private final List<ArgumentSpec> arguments;
    private final RunCustomCommand runner;
    private final Supplier<CustomCommand> live;

    public CustomCommandRegistration(
            CustomCommand command,
            List<ArgumentSpec> arguments,
            RunCustomCommand runner,
            Supplier<CustomCommand> live) {
        this.command = Objects.requireNonNull(command, "command");
        this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        this.runner = Objects.requireNonNull(runner, "runner");
        this.live = Objects.requireNonNull(live, "live");
    }

    /** A registration pinned to one definition, for a caller with no reloadable catalog behind it. */
    public CustomCommandRegistration(CustomCommand command, List<ArgumentSpec> arguments, RunCustomCommand runner) {
        this(command, arguments, runner, () -> command);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> literal =
                Commands.literal(command.literal().name());
        // Gate visibility on the declared node so a sender who cannot run the command does not see or complete it;
        // the use case re-checks before anything happens.
        command.permission()
                .ifPresent(node -> literal.requires(src -> src.getSender().hasPermission(node)));
        if (arguments.isEmpty()) {
            return literal.executes(this::run).build();
        }
        return literal.then(ArgumentNodes.chain(arguments, this::run)).build();
    }

    @Override
    public List<String> aliases() {
        return command.literal().aliases();
    }

    @Override
    public String description() {
        return command.usage().orElseGet(command::description);
    }

    @Override
    public String commandId() {
        return KEY_PREFIX + command.id().value();
    }

    @Override
    public String defaultName() {
        return command.literal().name();
    }

    @Override
    public List<String> defaultAliases() {
        return command.literal().aliases();
    }

    /** The per-locale aliases the definition declares, which the catalog merges with the operator's own. */
    public Map<String, List<String>> localizedAliases() {
        return command.literal().localizedAliases();
    }

    /** The definition this registration was built from, so the wiring can look one up without re-reading disk. */
    public CustomCommand definition() {
        return command;
    }

    private int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        Map<String, String> values = new LinkedHashMap<>(ArgumentNodes.read(ctx, arguments));
        values.put(RAW_ARGUMENTS, remainder(ctx.getInput()));
        RunOutcome outcome = runner.run(live.get(), CommandFeedback.refOf(sender), !(sender instanceof Player), values);
        return switch (outcome) {
            case RunOutcome.Ok ignored -> Command.SINGLE_SUCCESS;
            case RunOutcome.WarmupStarted ignored -> Command.SINGLE_SUCCESS;
            default -> 0;
        };
    }

    /** Everything the sender typed after the command word, or the empty string when they typed only the word. */
    private static String remainder(String input) {
        int space = input.indexOf(' ');
        return space < 0 ? "" : input.substring(space + 1).strip();
    }
}
