package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every key a shipped window names is one the catalogue answers.
 *
 * <p>A window names no words of its own. It writes {@code @menu.button.previous} and the catalogue holds
 * the words, and the two are in different files. Name a key nobody wrote and the engine draws the key
 * itself on the tile: the player reads {@code menu.button.previous} in a window that otherwise looks
 * finished, and nothing in the log says why.
 *
 * <p>A name covers the keys beneath it, because a tile that names a block reads the lines under that
 * block. {@code @menu.colour} is answered by {@code menu.colour.name} without the exact path being
 * written anywhere, which is how every tile in this estate is drawn.
 *
 * <p>Only what a spec writes between quotes is read, which is where a window names a key. A comment
 * counts, because a commented tile is one an operator uncomments, but prose does not: a header that says
 * "every label resolves through @keys" names no key, and a comment that writes {@code deposit@10} to show
 * a slot names no key either. Both of those are real lines in this estate.
 *
 * <p>A name with a placeholder in it is skipped. A tile whose block is chosen at runtime is written
 * {@code @menu.colour.%state%} and what it stands for is decided when it is drawn, so this test cannot
 * answer for it; {@code EveryKeyIsReadTest} reads those from the other side.
 *
 * <p>Only English is read. The other catalogues are held to English's exact key set by
 * {@code EveryLanguageAnswersEveryKeyTest}, so a key answered here is answered in every language or that
 * test fails first.
 */
final class EveryWindowKeyIsWrittenTest {

    /** Every window this plugin ships: one folder per module, plus the shared one. */
    private static final Path SPECS = Path.of("src", "main", "resources");

    private static final Path CATALOGUE = Path.of("src", "messages", "resources", "messages", "messages_en.conf");

    /** One quoted value of a spec, which is the only place a window names a key. */
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    /** One key inside such a value: an at sign and the path after it. */
    private static final Pattern NAMED = Pattern.compile("@([a-z0-9][a-z0-9._%-]*)");

    @Test
    @DisplayName("every key the shipped windows name is one the catalogue answers")
    void everyWindowKeyIsWritten() throws ConfigurateException {
        Set<String> written = catalogueKeys();
        List<String> unanswered = new ArrayList<>();

        for (String named : keysTheWindowsName()) {
            if (named.contains("%")) {
                continue;
            }
            boolean answered = written.contains(named) || written.stream().anyMatch(key -> key.startsWith(named + "."));
            if (!answered) {
                unanswered.add(named);
            }
        }

        assertThat(unanswered)
                .describedAs("a window names a key nobody wrote, so the engine draws the key itself on the"
                        + " tile. Write the line, or fix the name in the window")
                .isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothSidesAreRead() throws ConfigurateException {
        assertThat(keysTheWindowsName())
                .describedAs("this plugin ships a window that names keys, so finding none is a broken read")
                .isNotEmpty();
        assertThat(catalogueKeys())
                .describedAs("this plugin ships an English catalogue, so finding no keys is a broken read")
                .isNotEmpty();
    }

    /** Every key the shipped windows name, comments included: a commented tile is one an operator uncomments. */
    private static Set<String> keysTheWindowsName() {
        Set<String> named = new LinkedHashSet<>();
        for (Path file : menus()) {
            Matcher value = QUOTED.matcher(read(file));
            while (value.find()) {
                Matcher found = NAMED.matcher(value.group(1));
                while (found.find()) {
                    named.add(found.group(1));
                }
            }
        }
        return named;
    }

    /** Every path the English catalogue holds, flattened the way a window writes one. */
    private static Set<String> catalogueKeys() throws ConfigurateException {
        Set<String> keys = new LinkedHashSet<>();
        collect(HoconConfigurationLoader.builder().path(CATALOGUE).build().load(), "", keys);
        return keys;
    }

    private static void collect(ConfigurationNode node, String prefix, Set<String> into) {
        if (node.isMap()) {
            node.childrenMap()
                    .forEach((name, child) ->
                            collect(child, prefix.isEmpty() ? name.toString() : prefix + "." + name, into));
            return;
        }
        if (!prefix.isEmpty()) {
            into.add(prefix);
        }
    }

    /**
     * Every shipped window: a spec under a module's {@code gui/} folder or under the shared {@code menus/}
     * one. The words themselves are not a window and are read from the other side.
     */
    private static List<Path> menus() {
        if (!Files.isDirectory(SPECS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(SPECS)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .filter(path -> {
                        String where = path.toString().replace('\\', '/');
                        return where.contains("/gui/") || where.contains("/menus/");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + SPECS, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
