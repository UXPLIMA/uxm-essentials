package com.uxplima.uxmessentials.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Which context owns a command, and what the command says it does, read out of the source tree.
 *
 * <p>Half the modules publish their commands as {@link com.uxplima.uxmessentials.shared.application.module
 * .CommandSpec} rows the registry can be asked for; the other half register theirs from their own wiring class,
 * where nothing enumerable names the owner. The package a command class sits in does name it, since a context
 * package and a module id are the same word, so the owner is read from the path rather than from a second list
 * somebody would have to maintain. The command's own {@code description()} is read the same way, because that is
 * the sentence Paper already shows in the command listing.
 */
final class CommandOwners {

    private static final Pattern IMPLEMENTS_REGISTRATION =
            Pattern.compile("implements\\s+[^{]*\\bCommandRegistration\\b");
    private static final Pattern DEFAULT_NAME =
            Pattern.compile("String\\s+defaultName\\(\\)\\s*\\{\\s*return\\s*\"([^\"]+)\"");
    private static final Pattern ROOT_LITERAL = Pattern.compile("Commands\\.literal\\(\\s*([^)]*?)\\s*\\)");
    private static final Pattern LITERAL_CONSTANT =
            Pattern.compile("static final String LITERAL\\s*=\\s*\"([a-z0-9]+)\"");
    private static final Pattern SUPER_LITERAL = Pattern.compile("\\bsuper\\((?:[^;\")]*,\\s*)?\"([a-z0-9]+)\"");
    private static final Pattern PERMISSION_CONSTANT = Pattern.compile("String PERMISSION\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern PERMISSION_REFERENCE =
            Pattern.compile("String PERMISSION\\s*=\\s*(\\w+)\\.PERMISSION");
    private static final Pattern NODE_CONSTANT = Pattern.compile("String [A-Z_]+\\s*=\\s*\"(uxmessentials\\.[^\"]+)\"");
    private static final Pattern DESCRIPTION = Pattern.compile("String description\\(\\)\\s*\\{\\s*return\\s*([^;]+);");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final String PACKAGE_ROOT = "com/uxplima/uxmessentials/";

    record SourceCommand(String literal, String context, String permission, String description) {}

    private CommandOwners() {}

    static List<SourceCommand> read() {
        Path root = sourceRoot();
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("CommandRegistration.java"))
                    .sorted()
                    .forEach(
                            path -> sources.put(root.relativize(path).toString().replace('\\', '/'), read(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
        Map<String, String> permissionsByClass = new HashMap<>();
        sources.forEach((path, source) -> {
            String declared = declaredPermission(source);
            if (!declared.isEmpty()) {
                permissionsByClass.put(className(path), declared);
            }
        });
        List<SourceCommand> commands = new ArrayList<>();
        sources.forEach(
                (path, source) -> command(path, source, permissionsByClass).ifPresent(commands::add));
        commands.sort((left, right) -> left.literal().compareTo(right.literal()));
        return List.copyOf(commands);
    }

    private static Optional<SourceCommand> command(String path, String source, Map<String, String> byClass) {
        if (!IMPLEMENTS_REGISTRATION.matcher(source).find()) {
            return Optional.empty();
        }
        Optional<String> context = contextOf(path);
        Matcher description = DESCRIPTION.matcher(source);
        String says = description.find() ? sentence(description.group(1)) : "";
        return literalOf(source)
                .flatMap(literal ->
                        context.map(owner -> new SourceCommand(literal, owner, permissionOf(source, byClass), says)));
    }

    /**
     * The sentence a {@code description()} returns, whether it is one literal or several joined across lines. A
     * description that is built rather than written out returns nothing, so the node's own wording stands in.
     */
    private static String sentence(String expression) {
        StringBuilder text = new StringBuilder();
        Matcher quoted = QUOTED.matcher(expression);
        int end = 0;
        while (quoted.find()) {
            if (!expression.substring(end, quoted.start()).replace("+", "").isBlank()) {
                return "";
            }
            text.append(quoted.group(1));
            end = quoted.end();
        }
        return expression.substring(end).isBlank() ? text.toString() : "";
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    private static String className(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        String file = slash < 0 ? relativePath : relativePath.substring(slash + 1);
        return file.substring(0, file.length() - ".java".length());
    }

    private static Optional<String> contextOf(String relativePath) {
        if (!relativePath.startsWith(PACKAGE_ROOT)) {
            return Optional.empty();
        }
        String rest = relativePath.substring(PACKAGE_ROOT.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? Optional.empty() : Optional.of(rest.substring(0, slash));
    }

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

    /** The node a class states outright, which is also what another class pointing at it inherits. */
    private static String declaredPermission(String source) {
        Matcher permission = PERMISSION_CONSTANT.matcher(source);
        return permission.find() ? permission.group(1) : "";
    }

    /**
     * The node the command's root is gated by: the {@code PERMISSION} constant where a class declares one, the
     * node of the class it points at where it shares one, and otherwise the first node constant it holds, which
     * is the one the root {@code requires} reads.
     */
    private static String permissionOf(String source, Map<String, String> byClass) {
        String declared = declaredPermission(source);
        if (!declared.isEmpty()) {
            return declared;
        }
        Matcher reference = PERMISSION_REFERENCE.matcher(source);
        if (reference.find()) {
            String shared = byClass.get(reference.group(1));
            if (shared != null) {
                return shared;
            }
        }
        Matcher first = NODE_CONSTANT.matcher(source);
        return first.find() ? first.group(1) : "";
    }

    private static Path sourceRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir.resolve("bukkit-adapter").resolve("src/main/java");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
