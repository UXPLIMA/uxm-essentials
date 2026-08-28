package com.uxplima.uxmessentials.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The production Java of every module, for the guards that enforce a rule about source text rather than about
 * class identity (which is ArchUnit's job, see docs/05-testing.md §16).
 *
 * <p>The point of {@link #code(String)} is that a rule about what the code <em>does</em> must not be triggered by
 * a comment that merely <em>names</em> the forbidden thing. Half the mentions of {@code BukkitRunnable} in this
 * repository sit in javadoc explaining why it is never used; a guard that cannot tell those apart from a real call
 * is a guard nobody can keep green, and it gets deleted rather than obeyed.
 */
final class ProductionSources {

    /** Every Gradle module that ships production Java. */
    private static final List<String> MODULES = List.of(
            "api",
            "bukkit-api",
            "core",
            "bukkit-adapter",
            "persistence-adapter",
            "migration",
            "velocity-adapter",
            "redis-adapter",
            "discord-adapter",
            "rest-adapter");

    private ProductionSources() {}

    /** Every production {@code .java} file, across every module. */
    static List<Path> files() {
        List<Path> files = new ArrayList<>();
        for (String module : MODULES) {
            Path root = repoRoot().resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(path -> path.toString().endsWith(".java")).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException("could not walk " + root, e);
            }
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("no production sources found under " + repoRoot());
        }
        return files;
    }

    static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }

    /**
     * The source with every comment blanked out and every other character, string literals included, left where it
     * was. Positions are preserved so a line number taken from this text still points at the real line.
     */
    static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = copyLiteral(source, out, i, c);
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                out.append("  ");
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Copy one string or char literal verbatim, honouring escapes, and return the index just past it. */
    private static int copyLiteral(String source, StringBuilder out, int start, char quote) {
        int i = start;
        int n = source.length();
        out.append(source.charAt(i));
        i++;
        while (i < n) {
            char c = source.charAt(i);
            out.append(c);
            i++;
            if (c == '\\' && i < n) {
                out.append(source.charAt(i));
                i++;
                continue;
            }
            if (c == quote) {
                break;
            }
        }
        return i;
    }

    /** The 1-based line number of {@code index} in {@code text}. */
    static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate the repository root from " + Path.of("").toAbsolutePath());
    }
}
