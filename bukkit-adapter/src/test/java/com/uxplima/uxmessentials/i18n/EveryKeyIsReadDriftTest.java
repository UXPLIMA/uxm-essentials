package com.uxplima.uxmessentials.i18n;

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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKeyCatalog;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.menu.MenuKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when a line ships and nothing ever says it.
 *
 * <p>A key that no code sends and no window names is a promise printed in twelve languages that a player can
 * never be shown. It costs a translator their time, it tells a reviewer the feature is there, and every other
 * catalogue guard here is blind to it: {@code MessageKeyLocaleParityDriftTest} proves the twelve catalogues
 * carry exactly the constant set, which says nothing about whether anything reads them.
 *
 * <p>Sixty two of them were found by hand on 2026-09-22 and every one was resolved before this guard was
 * written, because a guard with sixty two written exemptions proves nothing. They were three kinds of thing.
 * A line another line already said (deleted). A window that drew nothing where the sentence belonged (the
 * four moderation empty states, the two back buttons). A confirmation the code forgot to send (the teleport
 * warmup, the item a player received, a sanction's own operator, the payer whose prompt lapsed). The list is
 * empty now and this test is what keeps it empty.
 *
 * <h2>What counts as read</h2>
 *
 * <ol>
 *   <li>The constant is named outside its own declaration, as {@code EnumName.CONSTANT}.
 *   <li>The constant is named bare inside its own message package, which is how a switch over the keys reads.
 *   <li>The key's path is named by a shipped file, which is how a menu spec names a tile.
 *   <li>The key's path is one the library looks up by name, which is the menu engine's own chrome.
 * </ol>
 *
 * <p>Comments are stripped before any of that is read. A javadoc is not a sender, and the hand scan that
 * produced the list of sixty two missed {@code MUTE_APPLIED} for exactly that reason: the key enum's own
 * class javadoc names it as the example of the constant-to-path convention.
 */
class EveryKeyIsReadDriftTest {

    /** Where a production source can live; every module of this repository. */
    private static final String SOURCE = "src/main/java";

    /** Where a shipped file can live. The words themselves are not a window naming a key. */
    private static final String RESOURCES = "src/main/resources";

    @Test
    @DisplayName("every key we ship is sent by the code, named by a window, or read by the library")
    void everyKeyIsRead() {
        List<Source> sources = sources();
        Set<String> namedInFiles = namesInShippedFiles();
        Set<String> libraryPaths = pathsTheLibraryReads();
        List<String> dead = new ArrayList<>();

        for (MessageKey key : MessageKeyCatalog.all()) {
            if (!(key instanceof Enum<?> constant)) {
                continue; // a lambda key belongs to whoever built it, and it cannot be dead in a catalogue
            }
            if (libraryPaths.contains(key.key()) || namedInFiles.contains(key.key()) || usedInCode(sources, constant)) {
                continue;
            }
            dead.add(constant.getDeclaringClass().getSimpleName() + "." + constant.name() + " (" + key.key() + ")");
        }

        assertThat(dead)
                .describedAs("Send the key, name it from a window, or take it out of the enum and out of every"
                        + " catalogue. A line nobody can read is not a translation, it is a claim.")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan reads this plugin, so it cannot pass by finding nothing")
    void theScanReadsThisPlugin() {
        assertThat(sources()).hasSizeGreaterThan(500);
        assertThat(MessageKeyCatalog.all()).hasSizeGreaterThan(1000);
        assertThat(namesInShippedFiles())
                .describedAs("the shipped menu specs name keys, so a resource scan that finds none is broken")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the library really reads the paths it is allowed to own")
    void theLibraryBlockIsTheLibrarysOwn() {
        Set<String> paths = pathsTheLibraryReads();

        assertThat(paths)
                .describedAs("read off the library's own constants rather than spelled here, so a renamed"
                        + " engine key cannot quietly widen this allowance")
                .contains("gui.confirm.yes", "gui.page.next", "gui.input.cancelled");
    }

    /**
     * Whether the constant is named anywhere outside the line that declares it.
     *
     * <p>The name and not the path: the path is what an operator edits and the constant is what a caller
     * sends. A key referenced only by its own declaration is a key nothing sends.
     */
    private static boolean usedInCode(List<Source> sources, Enum<?> key) {
        String qualified = key.getDeclaringClass().getSimpleName() + "." + key.name();
        String bare = key.name();
        String messagePackage = "package " + key.getDeclaringClass().getPackageName() + ";";
        for (Source source : sources) {
            String body = source.body();
            if (source.declares(key)) {
                body = withoutDeclarationOf(body, bare);
            }
            if (body.contains(qualified)) {
                return true;
            }
            // A switch over the keys inside the message package names the constant without its type, as the
            // key-to-path helpers do. The package declaration and not a mention of the package: an import
            // line holds the package name too, and reading that as a use lets any importer vouch for any
            // constant it happens to spell.
            if (body.contains(messagePackage) && namesBare(body, bare)) {
                return true;
            }
        }
        return false;
    }

    /** The source with the one line that declares {@code constant} taken out, so a declaration is not a use. */
    private static String withoutDeclarationOf(String body, String constant) {
        Pattern declaration = Pattern.compile("(?m)^\\s{4}" + Pattern.quote(constant) + "\\s*\\(");
        StringBuilder kept = new StringBuilder(body.length());
        for (String line : body.lines().toList()) {
            if (!declaration.matcher(line).find()) {
                kept.append(line);
            }
            kept.append('\n');
        }
        return kept.toString();
    }

    private static boolean namesBare(String body, String name) {
        int at = body.indexOf(name);
        while (at >= 0) {
            boolean beforeIsFree = at == 0 || !Character.isJavaIdentifierPart(body.charAt(at - 1));
            int after = at + name.length();
            boolean afterIsFree = after >= body.length() || !Character.isJavaIdentifierPart(body.charAt(after));
            if (beforeIsFree && afterIsFree) {
                return true;
            }
            at = body.indexOf(name, at + 1);
        }
        return false;
    }

    /**
     * Every catalogue path a shipped file names.
     *
     * <p>A menu spec writes {@code @warp.editor.welcome.name}, and a name covers every key under it, because
     * a tile that names a block draws the keys beneath it. Comments count: a shipped file teaches by example,
     * and an example an operator uncomments is a tile they will draw.
     */
    private static Set<String> namesInShippedFiles() {
        Set<String> named = new LinkedHashSet<>();
        for (Path file : filesUnder(RESOURCES)) {
            String text = read(file);
            int at = text.indexOf('@');
            while (at >= 0) {
                int end = at + 1;
                while (end < text.length()
                        && (Character.isLetterOrDigit(text.charAt(end)) || ".-_%".indexOf(text.charAt(end)) >= 0)) {
                    end++;
                }
                String name = text.substring(at + 1, end);
                if (!name.isEmpty()) {
                    named.add(name);
                    for (String path : MessageKeyCatalog.allKeys()) {
                        if (path.startsWith(name + ".")) {
                            named.add(path);
                        }
                    }
                }
                at = text.indexOf('@', end);
            }
        }
        return named;
    }

    /**
     * The paths uxmLib looks up by name, read off the library's own constants.
     *
     * <p>The menu engine draws its own chrome (the confirm buttons, the page arrows, the gesture names, the
     * colour picker) out of the plugin's catalogue, and the text input seam does the same for its prompts. No
     * constant of ours is ever named for those, so they are read without our code naming them. Reflecting
     * over the library's classes rather than spelling the paths keeps this allowance exactly as wide as the
     * library makes it.
     */
    private static Set<String> pathsTheLibraryReads() {
        Set<String> paths = new LinkedHashSet<>();
        for (Class<?> owner : List.of(MenuKeys.class, TextInput.class)) {
            for (Field field : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                try {
                    Object value = field.get(null);
                    if (value instanceof String path) {
                        paths.add(path);
                    }
                } catch (IllegalAccessException notReadable) {
                    // A private constant is not part of the library's published lookup set.
                }
            }
        }
        return paths;
    }

    /** Every production source of every module, with its comments taken out. */
    private static List<Source> sources() {
        List<Source> sources = new ArrayList<>();
        for (Path file : filesUnder(SOURCE)) {
            if (file.toString().endsWith(".java")) {
                sources.add(new Source(file, withoutComments(read(file))));
            }
        }
        return sources;
    }

    /**
     * The source with every comment taken out, string literals left alone.
     *
     * <p>Written as a scan rather than a regular expression on purpose: the obvious block-comment pattern
     * backtracks so deeply on this plugin's larger files that it overflows the stack, which is how the first
     * cut of this guard failed. A character walk is linear and cannot.
     */
    private static String withoutComments(String source) {
        StringBuilder kept = new StringBuilder(source.length());
        int at = 0;
        while (at < source.length()) {
            char here = source.charAt(at);
            char next = at + 1 < source.length() ? source.charAt(at + 1) : '\0';
            if (here == '"') {
                at = copyStringLiteral(source, at, kept);
            } else if (here == '\'') {
                at = copyCharLiteral(source, at, kept);
            } else if (here == '/' && next == '/') {
                while (at < source.length() && source.charAt(at) != '\n') {
                    at++;
                }
            } else if (here == '/' && next == '*') {
                at += 2;
                while (at + 1 < source.length() && !(source.charAt(at) == '*' && source.charAt(at + 1) == '/')) {
                    at++;
                }
                at = Math.min(at + 2, source.length());
                kept.append(' ');
            } else {
                kept.append(here);
                at++;
            }
        }
        return kept.toString();
    }

    /** Copy the string literal starting at {@code at} (quotes and escapes included) and return the index after it. */
    private static int copyStringLiteral(String source, int at, StringBuilder kept) {
        kept.append(source.charAt(at));
        int index = at + 1;
        while (index < source.length()) {
            char character = source.charAt(index);
            kept.append(character);
            if (character == '\\' && index + 1 < source.length()) {
                kept.append(source.charAt(index + 1));
                index += 2;
                continue;
            }
            index++;
            if (character == '"') {
                break;
            }
        }
        return index;
    }

    /** The same for a character literal, so a lone slash in one never opens a comment. */
    private static int copyCharLiteral(String source, int at, StringBuilder kept) {
        kept.append(source.charAt(at));
        int index = at + 1;
        while (index < source.length()) {
            char character = source.charAt(index);
            kept.append(character);
            if (character == '\\' && index + 1 < source.length()) {
                kept.append(source.charAt(index + 1));
                index += 2;
                continue;
            }
            index++;
            if (character == '\'') {
                break;
            }
        }
        return index;
    }

    /** Every file under {@code folder} of every module of this repository. */
    private static List<Path> filesUnder(String folder) {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> modules = Files.list(repoRoot())) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path root = module.resolve(folder);
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> tree = Files.walk(root)) {
                    tree.filter(Files::isRegularFile).sorted().forEach(found::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk the modules", e);
        }
        return found;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException notText) {
            return ""; // a png in the resources is not a window naming a key
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
        throw new IllegalStateException("could not locate the repository root");
    }

    /** One production source: where it is, and its text with the comments gone. */
    private record Source(Path file, String body) {

        /** Whether this is the file that declares {@code key}, whose declaration line must not count. */
        boolean declares(Enum<?> key) {
            return file.toString()
                    .replace('\\', '/')
                    .endsWith(key.getDeclaringClass().getSimpleName() + ".java");
        }
    }
}
