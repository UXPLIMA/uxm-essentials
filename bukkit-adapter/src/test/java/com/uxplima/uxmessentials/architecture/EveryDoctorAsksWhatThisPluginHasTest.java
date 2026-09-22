package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The doctor asks about what this plugin has.
 *
 * <p>An operator who cannot read a log has one way to ask how a plugin is, and what they may ask about was
 * written per plugin and drifted. uxmShop kept the shops, their offers, their stock and the log in a database
 * its doctor never mentioned. uxmQuests answered about its database and said nothing about the four windows it
 * draws, each of which is a file that can fail to parse. The answer an operator gets should depend on what can
 * go wrong, not on which product they bought.
 *
 * <p>Three of the checks are therefore not this plugin's choice: a plugin with a database reports its storage, a
 * plugin that loads windows reports how many were read, and a plugin that publishes a placeholder expansion
 * reports whether the plugin behind it is there. Everything else this doctor says is its own, and it says a
 * great deal: the transport, the cluster peers, the scheduler, the module count, the command conflicts and the
 * update checker.
 *
 * <p>Twenty five plugins have carried this guard for weeks. This one did not, and it is the product with the
 * most to answer for. The reason is worth keeping: the estate's copy reads {@code src/main/resources} and
 * {@code src/main/java} at the root of a repository, and this repository keeps ten modules, so the copy could
 * not have been dropped in. That is the same single-module assumption that hid three other gaps this month.
 *
 * <p>Both halves are read: the check has to exist as a class that answers to the name, and the composition root
 * has to construct it. A check nobody wires is a check nobody runs.
 */
final class EveryDoctorAsksWhatThisPluginHasTest {

    private static final Path HEALTH =
            Path.of("bukkit-adapter/src/main/java/com/uxplima/uxmessentials/bootstrap/health");

    private static final Path COMPOSITION =
            Path.of("bukkit-adapter/src/main/java/com/uxplima/uxmessentials/bootstrap/di/PluginModule.java");

    @Test
    @DisplayName("a plugin with a database reports its storage")
    void adatabaseIsReported() {
        assertThat(doctorSays("database"))
                .describedAs("a database that does not answer is every other symptom at once, and the window"
                        + " that would have said so is the window that cannot draw")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that loads operator windows reports how many were read")
    void thewindowsAreReported() {
        assertThat(sourceOf(COMPOSITION))
                .describedAs("this test is about the custom menus loader; if the loader goes, so does the test")
                .contains("CustomMenusWiring.wire(");
        assertThat(doctorSays("windows"))
                .describedAs("a window is a file an operator edits, and a file that did not parse is a menu"
                        + " that does not open with no line anywhere saying why")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that publishes an expansion reports whether anybody is listening")
    void theplaceholdersAreReported() {
        assertThat(sourceOf(COMPOSITION))
                .describedAs("this test is about the published expansion; if it goes, so does the test")
                .contains("PlaceholderApiSupport.registerExpansion(");
        assertThat(doctorSays("placeholders"))
                .describedAs("our placeholders in somebody else's scoreboard read as nothing when the plugin"
                        + " behind them is not there, and nothing else shows it")
                .isTrue();
    }

    @Test
    @DisplayName("the doctor is asked something, so the three above are read against a real one")
    void thedoctorIsAskedSomething() {
        assertThat(healthSources())
                .describedAs("a plugin whose health package is empty has a doctor that answers nothing, and"
                        + " the three tests above would pass by finding nothing to look at")
                .hasSizeGreaterThan(5);
        assertThat(sourceOf(COMPOSITION))
                .describedAs("the composition root is where a check is handed to the doctor")
                .contains("List<HealthCheck> healthChecks");
    }

    /**
     * Whether a check answering to {@code name} exists and is constructed by the composition root.
     *
     * <p>By the name the operator reads rather than by a class name, because that is the half of a check that
     * is a promise: {@code /uxmess doctor} prints it, and the estate rule is written in those words.
     */
    private static boolean doctorSays(String name) {
        String wiring = sourceOf(COMPOSITION);
        for (Path file : healthSources()) {
            String body = sourceOf(file);
            if (!body.contains("return \"" + name + "\"")) {
                continue;
            }
            String className = file.getFileName().toString().replace(".java", "");
            if (wiring.contains("new " + className + "(")) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> healthSources() {
        Path root = ProductionSources.repoRoot().resolve(HEALTH);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(root)) {
            tree.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + root, e);
        }
        return files;
    }

    private static String sourceOf(Path relative) {
        return ProductionSources.read(ProductionSources.repoRoot().resolve(relative));
    }
}
