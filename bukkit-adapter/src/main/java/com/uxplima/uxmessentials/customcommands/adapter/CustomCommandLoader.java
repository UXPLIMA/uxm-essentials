package com.uxplima.uxmessentials.customcommands.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentKind;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentList;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandDuration;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandCatalog;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads the operator's {@code commands/custom/*.conf} files into custom command definitions. Each top-level
 * {@code .conf} becomes one command whose id is the file name without its extension, so the file an operator edits
 * and the id every admin command names are the same thing.
 *
 * <p>One bad file never hides the rest: a parse failure, an unusable id, or an argument list the domain refuses
 * skips that file with a warning naming it and leaves every other definition loaded. Word collisions are settled
 * afterwards by {@link CustomCommandCatalog}, which claims words in load order, so the file that declared a word
 * first keeps it. An absent directory is normal on a fresh install and loads nothing without complaint.
 */
public final class CustomCommandLoader {

    /** The default a file that names no explicit console policy carries: the console may run the chain. */
    private static final boolean CONSOLE_ALLOWED_DEFAULT = true;

    private final Logger log;

    public CustomCommandLoader(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The outcome of one load pass: the resolved catalog, the Brigadier argument specs each surviving command needs
     * (keyed by id, so a registration never re-reads the file), the ids skipped over a bad file, and every warning
     * an operator should see, file warnings first and the catalog's collision warnings after.
     */
    public record LoadResult(
            CustomCommandCatalog.Loaded catalog,
            Map<String, List<ArgumentSpec>> argumentSpecs,
            List<String> skipped,
            List<String> warnings) {

        public LoadResult {
            Objects.requireNonNull(catalog, "catalog");
            argumentSpecs = Map.copyOf(Objects.requireNonNull(argumentSpecs, "argumentSpecs"));
            skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }

        /** The empty pass: no directory, or a directory holding no definitions. */
        public static LoadResult empty() {
            return new LoadResult(CustomCommandCatalog.Loaded.empty(), Map.of(), List.of(), List.of());
        }
    }

    /**
     * Read every {@code *.conf} directly under {@code directory} in file-name order. A missing directory yields an
     * empty result; a present one is read file by file, each independently, and the survivors are resolved through
     * the catalog so colliding words are settled once for the whole pass.
     */
    public LoadResult loadFrom(Path directory, ActionChain.ChainLimits limits) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(limits, "limits");
        if (!Files.isDirectory(directory)) {
            return LoadResult.empty();
        }
        List<Path> files;
        try (Stream<Path> entries = Files.list(directory)) {
            files = confFiles(entries);
        } catch (IOException failure) {
            log.warn(
                    "could not list custom command directory {} : {}", directory, String.valueOf(failure.getMessage()));
            return LoadResult.empty();
        }
        return read(files, limits);
    }

    /**
     * Re-read a single definition file, which is what {@code /customcmd reload <id>} needs. A file that is not there
     * yields an empty result; otherwise the outcome has the same shape as a whole-directory pass.
     */
    public LoadResult loadOne(Path file, ActionChain.ChainLimits limits) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(limits, "limits");
        if (!Files.isRegularFile(file)) {
            return LoadResult.empty();
        }
        return read(List.of(file), limits);
    }

    /** Read every file in order, then resolve the survivors through the catalog and fold both warning sources. */
    private LoadResult read(List<Path> files, ActionChain.ChainLimits limits) {
        List<CustomCommand> commands = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path file : files) {
            readOne(file, limits, warnings).ifPresentOrElse(commands::add, () -> skipped.add(idOf(file)));
        }
        CustomCommandCatalog.Loaded catalog = CustomCommandCatalog.of(commands);
        catalog.warnings().forEach(warning -> {
            warnings.add(warning);
            log.warn("custom commands: {}", warning);
        });
        Map<String, List<ArgumentSpec>> specs = new LinkedHashMap<>();
        for (CustomCommand command : catalog.commands()) {
            specs.put(command.id().value(), specsFor(command));
        }
        log.info("loaded {} custom commands, skipped {}", catalog.commands().size(), skipped.size());
        return new LoadResult(catalog, specs, skipped, warnings);
    }

    /**
     * Read one file into a definition, or empty when it cannot be used. Every refusal is logged with the file id so
     * an operator can find the file that caused it. Configurate's own failures and the domain's validation failures
     * are caught here; nothing catches {@code Throwable}.
     */
    private Optional<CustomCommand> readOne(Path file, ActionChain.ChainLimits limits, List<String> warnings) {
        String id = idOf(file);
        if (!CustomCommandId.valid(id)) {
            warn(warnings, id, "the file name is not a usable command id");
            return Optional.empty();
        }
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(file).build().load();
            return Optional.of(parse(CustomCommandId.of(id), root, limits, warnings));
        } catch (ConfigurateException | IllegalArgumentException invalid) {
            warn(warnings, id, String.valueOf(invalid.getMessage()));
            return Optional.empty();
        }
    }

    /** Turn one parsed file into a definition, or throw {@link IllegalArgumentException} when it cannot be one. */
    private CustomCommand parse(
            CustomCommandId id, ConfigurationNode root, ActionChain.ChainLimits limits, List<String> warnings) {
        ConfigurationNode command = root.node("command");
        CommandLiteral literal = literal(id, command, warnings);
        List<CommandArgument> arguments = arguments(id, root.node("arguments"), warnings);
        List<String> problems = ArgumentList.validate(arguments);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        ActionChain actions = actions(id, root, limits, warnings);
        ActionChain deny = ActionChain.of(strings(root.node("requirement-deny")), limits);
        deny.warnings().forEach(warning -> warn(warnings, id.value(), warning));
        return new CustomCommand(
                id,
                literal,
                optionalText(command.node("permission")),
                optionalText(command.node("deny-message")),
                command.node("console").getBoolean(CONSOLE_ALLOWED_DEFAULT),
                optionalText(command.node("description")).orElseGet(id::value),
                optionalText(command.node("usage")),
                duration(id, command.node("cooldown"), warnings),
                duration(id, command.node("warmup"), warnings),
                cost(id, command.node("cost"), warnings),
                arguments,
                strings(root.node("requirements")),
                deny,
                actions);
    }

    /** The words the command answers to: its primary name (the id when absent), its aliases and its per-locale ones. */
    private CommandLiteral literal(CustomCommandId id, ConfigurationNode command, List<String> warnings) {
        String name = optionalText(command.node("name")).orElseGet(id::value);
        if (!CommandLiteral.validWord(name)) {
            throw new IllegalArgumentException("the command name '" + name + "' is not a usable command word");
        }
        List<String> aliases = words(id, strings(command.node("aliases")), warnings);
        Map<String, List<String>> localized = new LinkedHashMap<>();
        command.node("localized-aliases").childrenMap().forEach((locale, node) -> {
            List<String> kept = words(id, strings(node), warnings);
            if (!kept.isEmpty()) {
                localized.put(String.valueOf(locale).toLowerCase(Locale.ROOT), kept);
            }
        });
        return new CommandLiteral(name, aliases, localized);
    }

    /** Keep the words that can be registered, dropping each unusable one with a warning naming it. */
    private List<String> words(CustomCommandId id, List<String> candidates, List<String> warnings) {
        List<String> kept = new ArrayList<>();
        for (String candidate : candidates) {
            if (CommandLiteral.validWord(candidate)) {
                kept.add(candidate.toLowerCase(Locale.ROOT));
            } else {
                warn(warnings, id.value(), "dropping the unusable command word '" + candidate + "'");
            }
        }
        return kept;
    }

    /** Read the ordered argument declarations; an unknown type degrades to a plain word rather than losing the file. */
    private List<CommandArgument> arguments(CustomCommandId id, ConfigurationNode node, List<String> warnings) {
        List<CommandArgument> arguments = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String name = optionalText(child.node("name"))
                    .orElseThrow(() -> new IllegalArgumentException("an argument declares no name"));
            String type = child.node("type").getString("string");
            ArgumentKind kind = ArgumentKind.parse(type).orElseGet(() -> {
                warn(warnings, id.value(), "the argument type '" + type + "' is unknown, reading it as a word");
                return ArgumentKind.STRING;
            });
            arguments.add(new CommandArgument(
                    name,
                    kind,
                    child.node("optional").getBoolean(false),
                    ArgumentKind.isRestToken(type),
                    bound(child.node("min")),
                    bound(child.node("max"))));
        }
        return arguments;
    }

    /**
     * The action chain the command runs. A file may write it out as {@code actions}, or use the {@code alias}
     * shorthand for a pure shortcut, but not both: two answers to what the command does is an operator mistake
     * worth naming rather than guessing at.
     */
    private ActionChain actions(
            CustomCommandId id, ConfigurationNode root, ActionChain.ChainLimits limits, List<String> warnings) {
        Optional<String> alias = optionalText(root.node("alias"));
        List<String> declared = strings(root.node("actions"));
        if (alias.isPresent() && !declared.isEmpty()) {
            throw new IllegalArgumentException("it declares both 'alias' and 'actions'");
        }
        List<String> tokens = alias.map(CustomCommandLoader::aliasChain).orElse(declared);
        ActionChain chain = ActionChain.of(tokens, limits);
        chain.warnings().forEach(warning -> warn(warnings, id.value(), warning));
        return chain;
    }

    /** Expand the {@code alias} shorthand into the one command action it stands for, forwarding the input. */
    private static List<String> aliasChain(String alias) {
        String target = alias.strip();
        if (target.startsWith("/")) {
            target = target.substring(1);
        }
        return List.of("command:" + target + " %args%");
    }

    /**
     * The Brigadier argument specs mirroring a command's declared arguments: the kind maps to the Brigadier node
     * type by name, a rest capture becomes a greedy read, and the numeric bounds carry straight across.
     */
    public static List<ArgumentSpec> specsFor(CustomCommand command) {
        Objects.requireNonNull(command, "command");
        List<ArgumentSpec> specs = new ArrayList<>();
        for (CommandArgument argument : command.arguments()) {
            specs.add(new ArgumentSpec(
                    argument.name(),
                    typeOf(argument.kind()),
                    argument.rest(),
                    argument.optional(),
                    argument.min(),
                    argument.max()));
        }
        return List.copyOf(specs);
    }

    /** The Brigadier argument type a domain argument kind names; the two enums are deliberately parallel. */
    private static ArgType typeOf(ArgumentKind kind) {
        return switch (kind) {
            case STRING -> ArgType.STRING;
            case INT -> ArgType.INT;
            case DOUBLE -> ArgType.DOUBLE;
            case BOOL -> ArgType.BOOL;
            case MATERIAL -> ArgType.MATERIAL;
            case WORLD -> ArgType.WORLD;
            case ONLINE_PLAYER -> ArgType.ONLINE_PLAYER;
            case PLAYER -> ArgType.PLAYER;
        };
    }

    /** A duration field, falling back to zero (and saying so) when the operator wrote something unreadable. */
    private Duration duration(CustomCommandId id, ConfigurationNode node, List<String> warnings) {
        String raw = node.getString();
        if (raw == null || raw.isBlank()) {
            return Duration.ZERO;
        }
        return CommandDuration.parse(raw).orElseGet(() -> {
            warn(warnings, id.value(), "the duration '" + raw + "' is unreadable, treating it as none");
            return Duration.ZERO;
        });
    }

    /** A cost field, clamped at zero: a negative price would pay the sender for running the command. */
    private double cost(CustomCommandId id, ConfigurationNode node, List<String> warnings) {
        double declared = node.getDouble(0);
        if (declared < 0) {
            warn(warnings, id.value(), "the cost " + declared + " is negative, treating it as free");
            return 0;
        }
        return declared;
    }

    /** An optional numeric bound on an argument, absent when the file declares none. */
    private static Optional<Double> bound(ConfigurationNode node) {
        return node.virtual() || node.empty() ? Optional.empty() : Optional.of(node.getDouble());
    }

    /** A text field, absent when it is missing or blank, so an empty config value never gates or shows anything. */
    private static Optional<String> optionalText(ConfigurationNode node) {
        String raw = node.getString();
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw.strip());
    }

    /** A string list field, empty when the file declares none or declares something that is not a list. */
    private static List<String> strings(ConfigurationNode node) {
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /** The {@code .conf} files directly under a directory, in file-name order so load order is stable. */
    private static List<Path> confFiles(Stream<Path> entries) {
        return entries.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".conf"))
                .sorted()
                .toList();
    }

    /** The id a file carries: its name without the {@code .conf} extension. */
    private static String idOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Record one operator-facing warning about a file, both in the load result and in the server log. */
    private void warn(List<String> warnings, String id, String detail) {
        warnings.add(id + ": " + detail);
        log.warn("custom command '{}': {}", id, detail);
    }
}
