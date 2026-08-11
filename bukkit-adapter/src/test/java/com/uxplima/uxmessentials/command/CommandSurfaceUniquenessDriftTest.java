package com.uxplima.uxmessentials.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.bootstrap.CommandAliasDefaults;
import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.command.CatalogWarning;
import com.uxplima.uxmessentials.shared.application.command.CommandCatalog;
import com.uxplima.uxmessentials.shared.application.command.CommandDefinition;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import org.junit.jupiter.api.Test;

/**
 * Guards the one thing {@link CommandCatalog} deliberately will not fail on: two commands claiming the same
 * root literal. The resolver is soft by design (an operator who renames {@code /home} to {@code /spawn} gets a
 * {@link CatalogWarning} and the loser dropped, not a crashed enable), which is right for operator config and
 * wrong for the surface we ship: a shipped collision silently costs a command, and the warning goes to a log
 * nobody reads. {@code /alts} reached a release that way.
 *
 * <p>{@code command-surface.txt} is the inventory of that shipped surface, one row per command
 * ({@code id name aliases}), and this test proves three things about it: no literal is claimed twice, the
 * shipped defaults resolve with zero warnings and zero drops, and the inventory still matches the code. The
 * last part is what keeps the file honest: every command class whose literal is written in its own source
 * (the common case) must have a row, and so must every module {@link CommandSpec}. A new command therefore
 * needs a row, and adding that row is where its literal meets every other literal in the plugin.
 *
 * <p>Commands whose literal only exists at wiring time (the parameterised teleport, pose, moderation and
 * time/weather families, and the operator's own {@code custommenus} commands) cannot be read out of a source
 * file, so they are covered by the inventory alone rather than by the source cross-check.
 */
class CommandSurfaceUniquenessDriftTest {

    private static final String INVENTORY = "/command-surface.txt";

    // Matches an aliases() override that returns a fixed list: List<String> aliases() { return List.of("a"); }
    private static final Pattern ALIASES = Pattern.compile(
            "List<String>\\s+aliases\\(\\)\\s*\\{\\s*return\\s+List\\.of\\(([^;]*?)\\);", Pattern.DOTALL);
    private static final Pattern DEFAULT_NAME =
            Pattern.compile("String\\s+defaultName\\(\\)\\s*\\{\\s*return\\s*\"([^\"]+)\"");
    private static final Pattern ROOT_LITERAL = Pattern.compile("Commands\\.literal\\(\\s*([^)]*?)\\s*\\)");
    private static final Pattern LITERAL_CONSTANT =
            Pattern.compile("static final String LITERAL\\s*=\\s*\"([a-z0-9]+)\"");
    private static final Pattern SUPER_LITERAL = Pattern.compile("\\bsuper\\((?:[^;\")]*,\\s*)?\"([a-z0-9]+)\"");
    private static final Pattern IMPLEMENTS_REGISTRATION =
            Pattern.compile("implements\\s+[^{]*\\bCommandRegistration\\b");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    void everyInventoryRowIsARegistrableCommand() {
        for (Row row : inventory()) {
            assertThatCode(() -> new CommandId(row.id()))
                    .as("command id '%s' must be a valid CommandId", row.id())
                    .doesNotThrowAnyException();
            for (String literal : row.literals()) {
                assertThat(literal)
                        .as("literal '%s' of command '%s' must be lowercase letters and digits", literal, row.id())
                        .matches("[a-z0-9]+");
            }
        }
    }

    @Test
    void noTwoCommandsClaimTheSameLiteral() {
        Map<String, String> owner = new LinkedHashMap<>();
        List<String> collisions = new ArrayList<>();
        for (Row row : inventory()) {
            for (String literal : row.literals()) {
                String previous = owner.putIfAbsent(literal, row.id());
                if (previous != null) {
                    collisions.add("'" + literal + "' is claimed by both '" + previous + "' and '" + row.id() + "'");
                }
            }
        }

        assertThat(collisions)
                .as("a literal claimed twice is silently dropped from the surface by CommandCatalog.resolve")
                .isEmpty();
    }

    @Test
    void theShippedDefaultsResolveWithoutDroppingALiteral() {
        List<Row> rows = inventory();
        List<CommandDefinition> definitions = CommandAliasDefaults.augment(rows.stream()
                .map(row -> new CommandDefinition(new CommandId(row.id()), row.name(), row.aliases()))
                .toList());

        CommandCatalog.Resolution resolution = CommandCatalog.resolve(definitions, Map.of(), true);

        assertThat(resolution.warnings())
                .as("the shipped command surface must resolve cleanly, with no operator override in play")
                .isEmpty();
        Map<String, EffectiveCommand> effective = new LinkedHashMap<>();
        resolution.effective().forEach(command -> effective.put(command.id().value(), command));
        for (CommandDefinition definition : definitions) {
            EffectiveCommand command = effective.get(definition.id().value());
            assertThat(command)
                    .as("command '%s' must survive resolution", definition.id().value())
                    .isNotNull();
            assertThat(command.name()).isEqualTo(definition.defaultName());
            assertThat(command.aliases())
                    .as(
                            "no default alias of '%s' may be dropped",
                            definition.id().value())
                    .containsExactlyElementsOf(definition.defaultAliases());
        }
    }

    @Test
    void everyCommandClassWithALiteralOfItsOwnHasAnInventoryRow() {
        Map<String, Row> byName = new LinkedHashMap<>();
        inventory().forEach(row -> byName.put(row.name(), row));
        List<String> missing = new ArrayList<>();
        List<String> unlisted = new ArrayList<>();
        List<CommandClass> classes = commandClasses();

        // A scan that stops matching (a renamed interface, a reshaped build method) would pass every
        // assertion below while checking nothing, so the count itself is part of the guard.
        assertThat(classes)
                .as("the source scan must still find the command classes it is built to read")
                .hasSizeGreaterThan(200);
        for (CommandClass command : classes) {
            Row row = byName.get(command.literal());
            if (row == null) {
                missing.add(command.file() + " registers /" + command.literal());
                continue;
            }
            for (String alias : command.aliases()) {
                if (!row.aliases().contains(alias)) {
                    unlisted.add(command.file() + " declares alias '" + alias + "' for /" + command.literal());
                }
            }
        }

        assertThat(missing)
                .as("every shipped command needs a row in command-surface.txt so its literal is checked for collisions")
                .isEmpty();
        assertThat(unlisted)
                .as("an alias declared in code but absent from the inventory escapes the collision check")
                .isEmpty();
    }

    @Test
    void everyModuleCommandSpecHasAnInventoryRow() {
        Set<String> ids = new LinkedHashSet<>();
        inventory().forEach(row -> ids.add(row.id()));
        List<String> missing = new DefaultModuleRegistry()
                .all().stream()
                        .flatMap(module -> module.commands().stream())
                        .map(CommandSpec::literal)
                        .filter(literal -> !ids.contains(literal))
                        .toList();

        assertThat(missing)
                .as("a module command missing from command-surface.txt is a command nothing checks for collisions")
                .isEmpty();
    }

    /** One row of the shipped inventory. */
    private record Row(String id, String name, List<String> aliases) {

        List<String> literals() {
            return Stream.concat(Stream.of(name), aliases.stream()).toList();
        }
    }

    /** One command class read out of the production source: the literal it registers and its fixed aliases. */
    private record CommandClass(String file, String literal, List<String> aliases) {}

    private static List<Row> inventory() {
        List<Row> rows = new ArrayList<>();
        for (String line : readInventory()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] columns = trimmed.split("\\s+");
            assertThat(columns).as("malformed inventory row '%s'", trimmed).hasSize(3);
            List<String> aliases = "-".equals(columns[2]) ? List.of() : List.of(columns[2].split(","));
            rows.add(new Row(columns[0], columns[1], aliases));
        }
        assertThat(rows).as("the command inventory must not be empty").isNotEmpty();
        return rows;
    }

    private static List<String> readInventory() {
        try (InputStream in = CommandSurfaceUniquenessDriftTest.class.getResourceAsStream(INVENTORY)) {
            assertThat(in).as("missing test resource %s", INVENTORY).isNotNull();
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + INVENTORY, failure);
        }
    }

    private static List<CommandClass> commandClasses() {
        Path root = sourceRoot();
        List<CommandClass> commands = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("CommandRegistration.java"))
                    .sorted()
                    .forEach(path -> read(root, path).ifPresent(commands::add));
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to walk " + root, failure);
        }
        return commands;
    }

    private static Optional<CommandClass> read(Path root, Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
        if (!IMPLEMENTS_REGISTRATION.matcher(source).find()) {
            return Optional.empty();
        }
        return literalOf(source)
                .map(literal -> new CommandClass(root.relativize(path).toString(), literal, aliasesOf(source)));
    }

    /**
     * The root literal a command class registers under, when its own source says so: an explicit
     * {@code defaultName()}, the quoted argument of the first {@code Commands.literal(...)} in {@code build()},
     * a {@code LITERAL} constant, or the literal it hands to its support superclass. A class that takes its
     * literal as a constructor parameter returns empty and is left to the inventory.
     */
    private static Optional<String> literalOf(String source) {
        Matcher defaultName = DEFAULT_NAME.matcher(source);
        if (defaultName.find()) {
            return Optional.of(defaultName.group(1));
        }
        int build = source.indexOf("build()");
        if (build >= 0) {
            Matcher root = ROOT_LITERAL.matcher(source.substring(build));
            if (root.find() && root.group(1).startsWith("\"")) {
                return Optional.of(root.group(1).replace("\"", ""));
            }
        }
        Matcher constant = LITERAL_CONSTANT.matcher(source);
        if (constant.find()) {
            return Optional.of(constant.group(1));
        }
        Matcher parent = SUPER_LITERAL.matcher(source);
        return parent.find() ? Optional.of(parent.group(1)) : Optional.empty();
    }

    private static List<String> aliasesOf(String source) {
        Matcher aliases = ALIASES.matcher(source);
        if (!aliases.find()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher token = STRING_LITERAL.matcher(aliases.group(1));
        while (token.find()) {
            tokens.add(token.group(1));
        }
        return List.copyOf(tokens);
    }

    private static Path sourceRoot() {
        Path source = repoRoot().resolve("bukkit-adapter").resolve("src/main/java");
        assertThat(Files.isDirectory(source))
                .as("expected the bukkit-adapter production source root at %s", source)
                .isTrue();
        return source;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
