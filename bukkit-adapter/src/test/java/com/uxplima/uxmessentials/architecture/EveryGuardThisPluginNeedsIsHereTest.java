package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This plugin carries the guards its own content asks for.
 *
 * <p>Every guard in the estate is copied from plugin to plugin by hand, and a plugin that grows a feature
 * after the copy never gets the guard that covers it. It has happened four times that anybody noticed:
 * uxmGlow gained a command branch and not its rename guard, the whole estate gained the action engine and
 * not the boss bar guard, three plugins gained effect lists and not the guard that reads them, and every
 * plugin written from the template starts without the three the template does not need.
 *
 * <p>Nothing is required of a plugin that does not have the thing. Two of the five checks below return
 * early here today: this product builds no {@code ActionContext} and ships no line of the engine's
 * grammar. That is the point of them. The day either arrives, this asks for the guard, which is the
 * moment the copy is otherwise forgotten.
 *
 * <p><strong>It asks what a guard reads and not what it is called.</strong> The estate's copy asks for
 * {@code EveryShippedSoundExistsTest} and {@code EveryShippedMaterialExistsTest} by name. Both of those
 * live here in one file called {@code ShippedNameDriftTest}, which is this repository's naming convention
 * and is older than the estate's. A scan that demanded the other names would be the scan wagging the
 * estate, so each check looks for a guard that names the platform type it is about.
 *
 * <p>Twenty five plugins have carried this for weeks and this one did not, for a reason worth keeping:
 * the estate's copy reads {@code src/main/resources} and {@code src/main/java} at the root of a
 * repository, and this repository keeps ten modules. That single-module assumption has hidden four gaps
 * this month and it is the same one every time.
 */
final class EveryGuardThisPluginNeedsIsHereTest {

    /** A line of the engine's grammar in a shipped file, which is what an effect list is made of. */
    private static final Pattern ACTION_LINE = Pattern.compile("\"\\[[a-z-]+\\][^\"]*\"");

    /** A sound named in a shipped file, in either of the two spellings this estate writes. */
    private static final Pattern SOUND =
            Pattern.compile("(?m)(\\[sound\\]|[a-z-]*sounds?\\s*[=:]\\s*\"?)[A-Za-z0-9_.:]*[._][A-Za-z0-9_.:]*");

    /** A material named in a shipped file, which is a screaming-case word under a material-ish key. */
    private static final Pattern MATERIAL =
            Pattern.compile("(?m)(material|icon|item)[a-z-]*\\s*=\\s*\"?[A-Z][A-Z0-9_]{2,}");

    @Test
    @DisplayName("a plugin that builds an action context guards the boss bar")
    void thebossBarGuardIsHere() {
        if (!sourceHolds("ActionContext.builder(")) {
            return;
        }
        assertThat(aGuardReads("bossbar"))
                .describedAs("[bossbar] puts a bar up and something has to take it down, and an unwired"
                        + " context throws after the bar is already on the player")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that writes an action line guards that the line is readable")
    void thereadableLineGuardIsHere() {
        if (!shippedMatches(ACTION_LINE)) {
            return;
        }
        assertThat(aGuardReadsShipped("ActionParser"))
                .describedAs("a line the engine cannot read takes its whole list down and does it quietly:"
                        + " the runner falls back to the plugin's own wording and nothing looks broken")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that ships a sound name guards the sound names")
    void thesoundGuardIsHere() {
        assertThat(shippedMatches(SOUND))
                .describedAs("this product ships a sound in nearly every window, so a scan that finds none"
                        + " is a broken scan rather than a quiet plugin")
                .isTrue();
        assertThat(aGuardReadsShipped("org.bukkit.Sound"))
                .describedAs("a sound the server does not have is silence, and silence is what a click that"
                        + " was meant to answer sounds like")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that ships a material name guards the material names")
    void thematerialGuardIsHere() {
        assertThat(shippedMatches(MATERIAL))
                .describedAs(
                        "this product ships a hundred windows, so a scan that finds no material is a" + " broken scan")
                .isTrue();
        assertThat(aGuardReadsShipped("Material.class"))
                .describedAs("a material the server does not know draws an empty tile and the window looks"
                        + " half built to the first player who opens it")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin whose commands can be renamed guards the catalogue that renames them")
    void thebranchGuardIsHere() {
        if (!sourceHolds("CommandId.of(")) {
            return;
        }
        assertThat(aGuardReads("CommandCatalog"))
                .describedAs("every command can be renamed, aliased and disabled, and a literal compiled in"
                        + " is a command an operator cannot touch")
                .isTrue();
    }

    @Test
    @DisplayName("the scan reads every module, so it cannot pass by finding nothing")
    void thescanReadsThePlugin() {
        assertThat(ProductionSources.files())
                .describedAs("ten modules of Java: a read that finds none is reading the wrong place")
                .hasSizeGreaterThan(100);
        assertThat(guards())
                .describedAs("this plugin has guards, so a scan that finds none is reading the wrong place")
                .hasSizeGreaterThan(50);
        assertThat(sourceHolds("CommandId.of("))
                .describedAs("this plugin renames its commands, so the branch check above is a real question")
                .isTrue();
    }

    /**
     * Whether some guard in this repository reads {@code token} against the files this plugin ships.
     *
     * <p>Both halves are needed and the first draft of this had only one. A test that merely names
     * {@code Material} is any test that builds an item, and there are dozens: hiding the real guard left
     * this passing, which is the failure a guard about guards must not have. So a guard about shipped
     * names has to be a test that walks {@code src/main/resources} as well, which is what makes it about
     * what we ship rather than about what the code does.
     *
     * <p>This file is skipped. It names every token it looks for, in quotes, and would otherwise answer
     * every question about itself.
     */
    private static boolean aGuardReadsShipped(String token) {
        return someGuard(body -> body.contains(token) && body.contains("src/main/resources"));
    }

    /** Whether some guard reads {@code token} at all, for a rule that is about code rather than content. */
    private static boolean aGuardReads(String token) {
        return someGuard(body -> body.contains(token));
    }

    private static boolean someGuard(java.util.function.Predicate<String> matches) {
        for (Path file : guards()) {
            if (file.getFileName().toString().equals("EveryGuardThisPluginNeedsIsHereTest.java")) {
                continue;
            }
            if (matches.test(read(file))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sourceHolds(String token) {
        for (Path file : ProductionSources.files()) {
            if (withoutSpaces(ProductionSources.read(file)).contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shippedMatches(Pattern pattern) {
        for (Path file : shipped()) {
            if (pattern.matcher(read(file)).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> guards() {
        return filesUnder("src/test/java", "Test.java");
    }

    private static List<Path> shipped() {
        return filesUnder("src/main/resources", ".conf");
    }

    /** Every file under {@code folder} of every module, which is what a ten module repository needs. */
    private static List<Path> filesUnder(String folder, String suffix) {
        List<Path> found = new ArrayList<>();
        for (String module : ProductionSources.MODULES) {
            Path root = ProductionSources.repoRoot().resolve(module).resolve(folder);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(suffix))
                        .sorted()
                        .forEach(found::add);
            } catch (IOException e) {
                throw new UncheckedIOException("could not walk " + root, e);
            }
        }
        return found;
    }

    private static String read(Path file) {
        return ProductionSources.read(file);
    }

    /**
     * The source with every space taken out, which is what a token search reads.
     *
     * <p>A guard that looks for a text fragment can be defeated by the formatter: {@code spotlessApply}
     * wraps a long chain at the dot, so the text this scan wants is never in the file on one line.
     */
    private static String withoutSpaces(String body) {
        return body.replaceAll("\\s+", "");
    }
}
