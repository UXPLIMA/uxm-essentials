package com.uxplima.uxmessentials.customcommands.adapter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.domain.ActionStep;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandDuration;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Turns a definition back into the file an operator would have written, the inverse of {@link CustomCommandLoader}.
 * It is what the in-game create wizard saves through, and what any future editor would write with: the loader turns
 * a file into a model, this turns a model into a file, and re-loading what it emits yields an equal definition.
 *
 * <p>The contract is model-faithful rather than byte-faithful. Only the keys a definition actually carries are
 * emitted, so a bare command writes a bare file; a zero cooldown, a zero warmup and a zero cost are absent rather
 * than written as zeros. The {@code alias} shorthand is written back as the {@code actions} list it expands to,
 * because un-expanding it would be guesswork and the expanded form behaves identically.
 *
 * <p>A chain's per-step offsets are written back as the {@code delay:} tokens that produced them: consecutive steps
 * sharing an offset are emitted once, with one delay token for each increase. That is the same shape a person
 * writes by hand, and it re-reads into the same offsets.
 */
public final class CustomCommandWriter {

    private CustomCommandWriter() {}

    /** The HOCON text for {@code command}, ready to be written to {@code <id>.conf}. */
    public static String render(CustomCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            CommentedConfigurationNode root = CommentedConfigurationNode.root(
                    HoconConfigurationLoader.builder().build().defaultOptions());
            writeCommandBlock(command, root.node("command"));
            writeArguments(command, root);
            writeRequirements(command, root);
            writeActions(command, root);
            StringWriter text = new StringWriter();
            HoconConfigurationLoader.builder()
                    .sink(() -> new BufferedWriter(text))
                    .build()
                    .save(root);
            return text.toString();
        } catch (ConfigurateException failure) {
            throw new IllegalStateException("could not render the definition '" + command.id() + "'", failure);
        }
    }

    /** Write {@code command} into {@code directory} as {@code <id>.conf} and hand back the file it wrote. */
    public static Path write(Path directory, CustomCommand command) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(command, "command");
        Files.createDirectories(directory);
        Path file = directory.resolve(command.id().value() + ".conf");
        Files.writeString(file, render(command), StandardCharsets.UTF_8);
        return file;
    }

    private static void writeCommandBlock(CustomCommand command, CommentedConfigurationNode block)
            throws ConfigurateException {
        block.comment("What the command is called and who may run it.");
        block.node("name").set(command.literal().name());
        if (!command.literal().aliases().isEmpty()) {
            block.node("aliases").set(command.literal().aliases());
        }
        if (!command.literal().localizedAliases().isEmpty()) {
            CommentedConfigurationNode localized = block.node("localized-aliases");
            for (var locale : command.literal().localizedAliases().entrySet()) {
                localized.node(locale.getKey()).set(locale.getValue());
            }
        }
        setIfPresent(block, "permission", command.permission());
        setIfPresent(block, "deny-message", command.denyMessage());
        block.node("console").set(command.consoleAllowed());
        block.node("description").set(command.description());
        setIfPresent(block, "usage", command.usage());
        if (!command.cooldown().isZero()) {
            block.node("cooldown").set(CommandDuration.format(command.cooldown()));
        }
        if (!command.warmup().isZero()) {
            block.node("warmup").set(CommandDuration.format(command.warmup()));
        }
        if (command.charged()) {
            block.node("cost").set(command.cost());
        }
    }

    private static void writeArguments(CustomCommand command, CommentedConfigurationNode root)
            throws ConfigurateException {
        if (command.arguments().isEmpty()) {
            return;
        }
        CommentedConfigurationNode arguments = root.node("arguments");
        arguments.comment("The positional arguments, in order; each value is readable as %arg_<name>%.");
        for (CommandArgument argument : command.arguments()) {
            CommentedConfigurationNode row = arguments.appendListNode();
            row.node("name").set(argument.name());
            row.node("type").set(argument.rest() ? "rest" : argument.kind().token());
            if (argument.optional()) {
                row.node("optional").set(true);
            }
            if (argument.min().isPresent()) {
                row.node("min").set(argument.min().get());
            }
            if (argument.max().isPresent()) {
                row.node("max").set(argument.max().get());
            }
        }
    }

    private static void writeRequirements(CustomCommand command, CommentedConfigurationNode root)
            throws ConfigurateException {
        if (!command.requirements().isEmpty()) {
            CommentedConfigurationNode node = root.node("requirements");
            node.comment("Every one of these has to pass before the command runs.");
            node.set(command.requirements());
        }
        if (!command.requirementDeny().isEmpty()) {
            CommentedConfigurationNode node = root.node("requirement-deny");
            node.comment("What runs instead when a requirement above fails.");
            node.set(tokensOf(command.requirementDeny().steps()));
        }
    }

    private static void writeActions(CustomCommand command, CommentedConfigurationNode root)
            throws ConfigurateException {
        if (command.actions().isEmpty()) {
            return;
        }
        CommentedConfigurationNode node = root.node("actions");
        node.comment("What the command does, in order.");
        node.set(tokensOf(command.actions().steps()));
    }

    /** Re-insert the {@code delay:} tokens the offsets came from, one for each increase along the chain. */
    private static List<String> tokensOf(List<ActionStep> steps) {
        List<String> tokens = new ArrayList<>();
        java.time.Duration carried = java.time.Duration.ZERO;
        for (ActionStep step : steps) {
            if (step.offset().compareTo(carried) > 0) {
                tokens.add("delay:" + CommandDuration.format(step.offset().minus(carried)));
                carried = step.offset();
            }
            tokens.add(step.token());
        }
        return tokens;
    }

    private static void setIfPresent(CommentedConfigurationNode block, String key, java.util.Optional<String> value)
            throws ConfigurateException {
        if (value.isPresent()) {
            block.node(key).set(value.get());
        }
    }
}
