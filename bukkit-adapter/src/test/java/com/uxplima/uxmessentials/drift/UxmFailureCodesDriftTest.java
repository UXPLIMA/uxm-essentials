package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import org.junit.jupiter.api.Test;

/**
 * The failure codes a consumer branches on are a published contract, so an implementation may not invent one.
 *
 * <p>The message beside a code is English and may be reworded at any time. The code may not: a plugin that
 * branches on {@code player-offline} keeps working across releases only if nothing else ever answers with a code
 * of its own devising. This reads every published action implementation and holds it to two rules: a code is
 * always written as a constant on {@link UxmFailure}, never as a literal, and the constant it names exists.
 *
 * <p>Source is read rather than reflected because a code lives inside a method body, where reflection cannot see
 * it. The three factories below are the only ways a failure is built.
 */
class UxmFailureCodesDriftTest {

    /** Every way a failure enters the API, each taking the code first. */
    private static final List<String> FACTORIES = List.of("UxmFailure.of(", "UxmOutcome.failed(", "UxmResult.failed(");

    @Test
    void everyCodeAnImplementationReturnsIsAPublishedConstant() {
        Set<String> published = publishedConstants();
        List<String> offenders = new ArrayList<>();

        for (Path file : implementations()) {
            String source = read(file);
            for (String argument : codeArguments(source)) {
                if (argument.startsWith("\"")) {
                    offenders.add(file.getFileName() + " writes the code inline: " + argument);
                } else if (argument.startsWith("UxmFailure.") && !published.contains(constantName(argument))) {
                    offenders.add(file.getFileName() + " names a constant that does not exist: " + argument);
                }
            }
        }

        assertThat(offenders)
                .as("a published failure code is a constant on UxmFailure and nothing else: a literal cannot be "
                        + "branched on safely, and a constant that does not exist would not compile for a consumer")
                .isEmpty();
    }

    @Test
    void thereIsSomethingToCheck() {
        assertThat(implementations()).isNotEmpty();
        assertThat(publishedConstants()).isNotEmpty();
    }

    /** The value of every {@code public static final String} on {@code UxmFailure}. */
    private static Set<String> publishedConstants() {
        Set<String> names = new TreeSet<>();
        for (Field field : UxmFailure.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType().equals(String.class)) {
                names.add(field.getName());
            }
        }
        return names;
    }

    /** Every published action implementation, which is where a code can be invented. */
    private static List<Path> implementations() {
        Path root = repoRoot().resolve("bukkit-adapter/src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.getFileName().toString().endsWith("Actions.java"))
                    .filter(path -> path.getParent().toString().endsWith("adapter/outbound/api"))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to walk " + root, failure);
        }
    }

    /** The first argument of every failure factory call in {@code source}, whitespace collapsed away. */
    private static List<String> codeArguments(String source) {
        List<String> arguments = new ArrayList<>();
        for (String factory : FACTORIES) {
            int from = 0;
            while (true) {
                int at = source.indexOf(factory, from);
                if (at < 0) {
                    break;
                }
                from = at + factory.length();
                arguments.add(firstArgument(source, from));
            }
        }
        return arguments;
    }

    /** Read forward from {@code start} to the comma or bracket that ends the first argument. */
    private static String firstArgument(String source, int start) {
        StringBuilder argument = new StringBuilder();
        int depth = 0;
        for (int at = start; at < source.length(); at++) {
            char character = source.charAt(at);
            if (depth == 0 && (character == ',' || character == ')')) {
                break;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (!Character.isWhitespace(character)) {
                argument.append(character);
            }
        }
        return argument.toString();
    }

    private static String constantName(String argument) {
        return argument.substring("UxmFailure.".length());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + file, failure);
        }
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
