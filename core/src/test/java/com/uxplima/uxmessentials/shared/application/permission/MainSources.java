package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the main sources of every module, for the permission tests that must see which nodes the code builds at
 * runtime. A cooldown or warmup kind names its feature in code, never in the catalogue, so the only way to know which
 * features exist is to read where the kinds are made.
 */
final class MainSources {

    /** A warmup or cooldown kind built in the code, with its first argument: the feature, written or named. */
    private static final Pattern KIND =
            Pattern.compile("(?:new\\s+(?:[\\w.]+\\.)?(WarmupKind)|new\\s+(?:[\\w.]+\\.)?(CooldownKind)"
                    + "|(?:[\\w.]+\\.)?CooldownKind\\.(?:scoped|onTeleport)|(?<![\\w.])(scoped|onTeleport))"
                    + "\\(\\s*(\"[^\"]*\"|[A-Za-z_][A-Za-z0-9_]*)");

    /** A file that may call the factories bare, because it imports them statically. */
    private static final Pattern STATIC_FACTORY =
            Pattern.compile("import\\s+static\\s+[\\w.]*CooldownKind\\.(?:scoped|onTeleport|\\*)\\s*;");

    /** The ports declare the kinds and pass the feature through; every other file names a real feature. */
    private static final Set<String> PORTS = Set.of("Cooldowns.java", "Warmups.java");

    private MainSources() {}

    /**
     * One kind the code builds.
     *
     * @param file the file that builds it
     * @param warmup whether it is a warmup rather than a cooldown
     * @param feature the feature it names, or empty when the argument is not a literal or a constant in that file
     * @param argument the first argument as written, for a failure message
     */
    record Kind(String file, boolean warmup, Optional<String> feature, String argument) {}

    /** Every cooldown and warmup kind the main sources build, the ports' own pass-through declarations left out. */
    static List<Kind> kinds() {
        List<Kind> kinds = new ArrayList<>();
        for (Path file : files()) {
            String source = read(file);
            String name = file.getFileName().toString();
            Matcher kind = KIND.matcher(source);
            boolean staticFactories = STATIC_FACTORY.matcher(source).find();
            while (kind.find()) {
                if (kind.group(3) != null && !staticFactories) {
                    continue;
                }
                Optional<String> feature = featureOf(source, kind.group(4));
                if (feature.isEmpty() && PORTS.contains(name)) {
                    continue;
                }
                kinds.add(new Kind(name, kind.group(1) != null, feature, kind.group(4)));
            }
        }
        return kinds;
    }

    static List<Path> files() {
        String root = System.getProperty("uxm.repositoryRoot");
        assertThat(root)
                .as("the build passes uxm.repositoryRoot to the test JVM")
                .isNotNull();
        try (Stream<Path> modules = Files.list(Path.of(root))) {
            List<Path> files = new ArrayList<>();
            for (Path module : modules.toList()) {
                Path sources = module.resolve("src/main/java");
                if (Files.isDirectory(sources)) {
                    try (Stream<Path> walk = Files.walk(sources)) {
                        walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
                    }
                }
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The feature a kind's first argument names: a literal, or a string constant declared in the same file. */
    private static Optional<String> featureOf(String source, String argument) {
        if (argument.startsWith("\"")) {
            return Optional.of(argument.substring(1, argument.length() - 1));
        }
        Matcher constant = Pattern.compile("String\\s+" + Pattern.quote(argument) + "\\s*=\\s*\"([^\"]+)\"")
                .matcher(source);
        return constant.find() ? Optional.of(constant.group(1)) : Optional.empty();
    }
}
