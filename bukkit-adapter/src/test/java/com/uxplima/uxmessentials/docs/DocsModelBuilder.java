package com.uxplima.uxmessentials.docs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSpec;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;

/**
 * Assembles what a module page's generated tables need. Everything here is read from a source the build
 * already guards: the registry for which modules exist, the two catalogues for the nodes and keys each one
 * owns, {@code command-surface.txt} for aliases, and the shipped config for the settings.
 */
final class DocsModelBuilder {

    private static final String INVENTORY = "/command-surface.txt";

    private DocsModelBuilder() {}

    static List<DocsData.Module> build() {
        Map<String, List<String>> aliases = aliases();
        List<CommandOwners.SourceCommand> sourceCommands = CommandOwners.read();
        List<DocsData.Module> modules = new ArrayList<>();
        for (var module : new DefaultModuleRegistry().all()) {
            String id = module.id().value();
            List<DocsData.Setting> settings = ShippedConfigReader.read(id);
            modules.add(new DocsData.Module(
                    id,
                    "modules/" + id + "/config.conf",
                    settings.stream()
                            .anyMatch(setting -> setting.key().equals("enabled")
                                    && setting.value().equals("true")),
                    commandsOf(module, id, sourceCommands, aliases),
                    PermissionCatalog.forArea(id).stream()
                            .map(permission -> new DocsData.Permission(
                                    permission.node(),
                                    permission.fallback().name(),
                                    permission.shape().name(),
                                    permission.description()))
                            .sorted(Comparator.comparing(DocsData.Permission::node))
                            .toList(),
                    settings.stream()
                            .filter(setting -> !setting.key().equals("enabled"))
                            .toList(),
                    PlaceholderCatalog.forArea(id).stream()
                            .map(placeholder -> new DocsData.Placeholder(
                                    placeholder.key(), placeholder.scope().name(), placeholder.description()))
                            .sorted(Comparator.comparing(DocsData.Placeholder::key))
                            .toList()));
        }
        modules.sort(Comparator.comparing(DocsData.Module::id));
        return List.copyOf(modules);
    }

    /**
     * Every command the module ships: the specs it publishes to the registry, plus the classes its own context
     * package holds for the modules that register from their wiring instead. A literal claimed both ways is
     * listed once, with the spec winning, since that is the registration the plugin actually runs.
     */
    private static List<DocsData.Command> commandsOf(
            FeatureModule module,
            String id,
            List<CommandOwners.SourceCommand> sourceCommands,
            Map<String, List<String>> aliases) {
        Map<String, DocsData.Command> byLiteral = new LinkedHashMap<>();
        for (CommandOwners.SourceCommand command : sourceCommands) {
            if (command.context().equals(id)) {
                byLiteral.put(command.literal(), command(command.literal(), command.permission(), aliases));
            }
        }
        for (CommandSpec spec : module.commands()) {
            byLiteral.put(spec.literal(), command(spec.literal(), spec.permission(), aliases));
        }
        return byLiteral.values().stream()
                .sorted(Comparator.comparing(DocsData.Command::literal))
                .toList();
    }

    private static DocsData.Command command(String literal, String permission, Map<String, List<String>> aliases) {
        return new DocsData.Command(
                literal,
                aliases.getOrDefault(literal, List.of()),
                permission,
                describe(PermissionCatalog.find(permission)
                        .map(PermissionSpec::description)
                        .orElse("")));
    }

    /**
     * A node's description opens with the command it gates ("/home to open your homes"), which reads as a
     * stutter in a table whose first column is already the command. Drop that opening and start the sentence at
     * the verb instead.
     */
    private static String describe(String description) {
        if (!description.startsWith("/")) {
            return description;
        }
        int separatorAt = -1;
        int verb = -1;
        for (String separator : List.of(" to ", ": ")) {
            int at = description.indexOf(separator);
            if (at >= 0 && (separatorAt < 0 || at < separatorAt)) {
                separatorAt = at;
                verb = at + separator.length();
            }
        }
        if (verb < 0 || verb >= description.length()) {
            return description;
        }
        return Character.toUpperCase(description.charAt(verb)) + description.substring(verb + 1);
    }

    private static Map<String, List<String>> aliases() {
        Map<String, List<String>> byLiteral = new HashMap<>();
        try (InputStream in = DocsModelBuilder.class.getResourceAsStream(INVENTORY)) {
            if (in == null) {
                throw new IllegalStateException("no " + INVENTORY + " on the classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line : reader.lines().toList()) {
                    String row = line.strip();
                    if (row.isEmpty() || row.startsWith("#")) {
                        continue;
                    }
                    List<String> columns = whitespaceSeparated(row);
                    if (columns.size() < 3) {
                        continue;
                    }
                    String aliases = columns.get(2);
                    byLiteral.put(columns.get(1), aliases.equals("-") ? List.of() : commaSeparated(aliases));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + INVENTORY, e);
        }
        return byLiteral;
    }

    /**
     * Splits on runs of whitespace. Written out rather than reached for through {@code String.split}, whose
     * trailing-empty-string behaviour Error Prone rejects on a shared build.
     */
    private static List<String> whitespaceSeparated(String row) {
        List<String> columns = new ArrayList<>();
        int index = 0;
        while (index < row.length()) {
            while (index < row.length() && Character.isWhitespace(row.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < row.length() && !Character.isWhitespace(row.charAt(index))) {
                index++;
            }
            if (index > start) {
                columns.add(row.substring(start, index));
            }
        }
        return List.copyOf(columns);
    }

    private static List<String> commaSeparated(String value) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                String part = value.substring(start, index).strip();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                start = index + 1;
            }
        }
        return List.copyOf(parts);
    }
}
