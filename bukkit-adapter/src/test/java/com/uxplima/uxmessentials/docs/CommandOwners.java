package com.uxplima.uxmessentials.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Which context owns a command, read out of the source tree.
 *
 * <p>Half the modules publish their commands as {@link com.uxplima.uxmessentials.shared.application.module
 * .CommandSpec} rows the registry can be asked for; the other half register theirs from their own wiring class,
 * where nothing enumerable names the owner. The package a command class sits in does name it, since a context
 * package and a module id are the same word, so the owner is read from the path rather than from a second list
 * somebody would have to maintain.
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
    private static final String PACKAGE_ROOT = "com/uxplima/uxmessentials/";

    record SourceCommand(String literal, String context, String permission) {}

    private CommandOwners() {}

    static List<SourceCommand> read() {
        Path root = sourceRoot();
        List<SourceCommand> commands = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("CommandRegistration.java"))
                    .sorted()
                    .forEach(path -> read(root, path).ifPresent(commands::add));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + root, e);
        }
        return List.copyOf(commands);
    }

    private static Optional<SourceCommand> read(Path root, Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
        if (!IMPLEMENTS_REGISTRATION.matcher(source).find()) {
            return Optional.empty();
        }
        Optional<String> context = contextOf(root.relativize(path).toString().replace('\\', '/'));
        return literalOf(source)
                .flatMap(literal -> context.map(owner -> new SourceCommand(literal, owner, permissionOf(source))));
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

    private static String permissionOf(String source) {
        Matcher permission = PERMISSION_CONSTANT.matcher(source);
        return permission.find() ? permission.group(1) : "";
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
