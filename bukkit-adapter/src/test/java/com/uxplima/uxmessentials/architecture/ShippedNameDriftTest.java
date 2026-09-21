package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.Sound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shipped-name drift guard: every material and every sound a shipped file names is one the server has.
 *
 * <p>Both are the cheapest defect in the estate to ship and the hardest to report. A material the platform does not
 * know draws nothing, so a window looks half built and the only sign is a line in a log an operator reads when
 * something else has already gone wrong. A sound it does not have is silence, and silence is what a click that was
 * meant to answer sounds like: nothing throws, and nobody can tell a missing sound from a sound they cannot hear.
 * A name is also the easiest thing in a file to misspell, because both spellings are legal where it is written and
 * neither is checked until the window opens.
 *
 * <p>Twenty two plugins of the estate carry the single-module form of this. This product's files are spread over
 * every module and over the {@code modules/} tree, so it walks the repository instead of one resources folder.
 *
 * <p>Both spellings of a sound ship: {@code block.note_block.pling} is the key the server prints and
 * {@code BLOCK_NOTE_BLOCK_PLING} is the constant an operator copies out of a wiki, and uxmLib answers either. The
 * fold runs the way uxmLib folds, forwards from the key to the constant. The names come off {@code Material} and
 * {@code Sound} as field names and never as values, so the guard needs no registry and no server, and reads exactly
 * what this build compiles against.
 */
class ShippedNameDriftTest {

    /** A material named as a value, as {@code material = STONE} or {@code icon = "PAPER"}. */
    private static final Pattern MATERIAL = Pattern.compile("(?m)^\\s*(?:material|icon)\\s*=\\s*\"?([^\"\n,}]+)\"?");

    /**
     * A sound named as the value of a key, as {@code sound = "block.chest.open"}.
     *
     * <p>The value has to carry a dot or an underscore, which every sound name does and no switch does:
     * {@code sound = false} is a channel being turned off and not a sound called false.
     */
    private static final Pattern SOUND =
            Pattern.compile("(?m)^\\s*[a-z-]*sounds?\\s*=\\s*\"?([A-Za-z0-9:]+[._][A-Za-z0-9_.:]*)");

    /** A sound inside a block of its own, as {@code sound { enabled = true, key = "block.bell.use" } }. */
    private static final Pattern SOUND_BLOCK = Pattern.compile("sounds?\\s*\\{[^}]*?key\\s*=\\s*\"([A-Za-z0-9_.:]+)\"");

    /** A sound named inside an action list, as {@code sound:BLOCK_NOTE_BLOCK_PLING 0.6 1.5}. */
    private static final Pattern SOUND_ACTION = Pattern.compile("sound:([A-Za-z0-9_.]+)");

    @Test
    @DisplayName("every material a shipped file names is one the server has")
    void everyshippedMaterialExists() {
        List<String> unknown = new ArrayList<>();
        Set<String> known = constantsOf(Material.class);

        for (Path file : shippedFiles()) {
            for (String written : namesIn(read(file), MATERIAL)) {
                String name = materialIn(written);
                if (!name.isEmpty() && !known.contains(name.toUpperCase(Locale.ROOT)) && !writtenInJava(written)) {
                    unknown.add(file.getFileName() + ": " + written);
                }
            }
        }

        assertThat(unknown)
                .describedAs("A material the server does not know draws nothing, and an empty tile is a window"
                        + " that reads as half built to the first player who opens it.")
                .isEmpty();
    }

    @Test
    @DisplayName("every sound a shipped file names is one the server has")
    void everyshippedSoundExists() {
        List<String> unknown = new ArrayList<>();
        Set<String> known = constantsOf(Sound.class);

        for (Path file : shippedFiles()) {
            for (String written : namesIn(read(file), SOUND, SOUND_BLOCK, SOUND_ACTION)) {
                if (!known.contains(afterTheLastColon(written).replace('.', '_').toUpperCase(Locale.ROOT))) {
                    unknown.add(file.getFileName() + ": " + written);
                }
            }
        }

        assertThat(unknown)
                .describedAs("A sound the server does not have is silence, and silence is what a click that was"
                        + " meant to answer sounds like.")
                .isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so neither of these can pass by finding nothing")
    void bothsidesAreRead() {
        assertThat(constantsOf(Material.class))
                .describedAs("the platform names its materials in Material, so an empty set is a broken read")
                .hasSizeGreaterThan(100);
        assertThat(constantsOf(Sound.class))
                .describedAs("the platform names its sounds in Sound, so an empty set is a broken read")
                .hasSizeGreaterThan(100);

        List<String> materials = new ArrayList<>();
        List<String> sounds = new ArrayList<>();
        for (Path file : shippedFiles()) {
            String body = read(file);
            materials.addAll(namesIn(body, MATERIAL));
            sounds.addAll(namesIn(body, SOUND, SOUND_BLOCK, SOUND_ACTION));
        }
        assertThat(materials)
                .describedAs("this product ships windows, so a material scan that finds none is a broken scan")
                .isNotEmpty();
        assertThat(sounds)
                .describedAs("this product ships sounds, so a sound scan that finds none is a broken scan")
                .isNotEmpty();
    }

    /** What the platform is asked about: the text after the last colon, so a provider in front of a name is not it. */
    private static String afterTheLastColon(String written) {
        String name = written.strip();
        int namespace = name.lastIndexOf(':');
        return namespace < 0 ? name : name.substring(namespace + 1);
    }

    /**
     * The material in this value, or nothing when the value is not one.
     *
     * <p>A tile may name a provider instead of a material, and a provider takes an argument of its own:
     * {@code tinted:LEATHER_CHESTPLATE} is a material with a colour in front of it, and
     * {@code basehead:<texture>} is a head drawn from a skin texture, which is a long base64 string and no
     * material at all. A provider's argument is read as a material only when it is written the way a material is
     * written, in capitals, which is how every file in this product writes one.
     */
    private static String materialIn(String written) {
        String value = written.strip();
        if (value.indexOf(':') < 0) {
            return value;
        }
        String argument = afterTheLastColon(value);
        return PLAIN_NAME.matcher(argument).matches() ? argument : "";
    }

    /** A material as a file writes one: capitals and underscores, and nothing else. */
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    /**
     * Whether this product's own Java writes the name as a string.
     *
     * <p>An icon provider is named in the {@code material} of a tile and answered by the icon registry, so the
     * platform has never heard of it and the file is right anyway.
     */
    private static boolean writtenInJava(String written) {
        for (Path file : ProductionSources.files()) {
            if (read(file).contains("\"" + written.strip() + "\"")) {
                return true;
            }
        }
        return false;
    }

    /** Every name this body writes that any of {@code patterns} reads, placeholders left out. */
    private static List<String> namesIn(String body, Pattern... patterns) {
        String kept = withoutComments(body);
        List<String> found = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher named = pattern.matcher(kept);
            while (named.find()) {
                String written = named.group(1).strip();
                if (!written.isEmpty() && written.indexOf('%') < 0 && written.indexOf('<') < 0) {
                    found.add(written);
                }
            }
        }
        return found;
    }

    /** Every name the platform holds in {@code table}, read as names and never as values. */
    private static Set<String> constantsOf(Class<?> table) {
        Set<String> found = new LinkedHashSet<>();
        for (Field field : table.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == table) {
                found.add(field.getName());
            }
        }
        return found;
    }

    /** Every file a first boot writes out, apart from the words, which have keys of these names too. */
    private static List<Path> shippedFiles() {
        try (Stream<Path> files = Files.walk(repoRoot())) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .filter(path -> path.toString().contains("/src/main/resources/"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .filter(path -> !path.toString().contains("messages"))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not walk " + repoRoot(), unreadable);
        }
    }

    private static String withoutComments(String body) {
        StringBuilder kept = new StringBuilder(body.length());
        for (String line : body.split("\n", -1)) {
            if (!line.strip().startsWith("#")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + path, unreadable);
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
        throw new IllegalStateException(
                "could not locate the repository root from " + Path.of("").toAbsolutePath());
    }
}
