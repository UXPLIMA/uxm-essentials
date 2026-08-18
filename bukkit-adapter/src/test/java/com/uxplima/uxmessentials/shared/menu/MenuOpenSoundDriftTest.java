package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every shipped menu opens with a sound. A window that appears in silence reads as a window that half-loaded, and
 * the style canon makes the opening page turn part of the look rather than a per-menu decision, so a new spec that
 * forgets it is drift a maintainer should meet at build time.
 *
 * <p>Only the opening effect is checked here. Click feedback is played by the engine for every gesture it accepts,
 * so it needs no per-item declaration and cannot drift out of a spec.
 */
class MenuOpenSoundDriftTest {

    private static final String SOUND_ACTION = "sound";

    @Test
    void everyShippedSpecOpensWithASound() {
        Path modulesDir = repoRoot().resolve("bukkit-adapter/src/main/resources/modules");
        if (!Files.isDirectory(modulesDir)) {
            return;
        }

        MenuSpecLoader loader = new MenuSpecLoader();
        List<String> silent = new ArrayList<>();
        for (Path conf : engineSpecs(modulesDir)) {
            MenuSpec spec = loader.load(conf);
            if (spec.openActions().stream().map(Ref::id).noneMatch(SOUND_ACTION::equals)) {
                silent.add(modulesDir.relativize(conf).toString());
            }
        }

        assertThat(silent)
                .as("shipped menu specs that open in silence; give each an open-actions sound effect "
                        + "(see docs/14-ui-style.md)")
                .isEmpty();
    }

    /** Every engine spec bundled under a module's {@code gui/} folder, told apart from a layout conf by its items. */
    private static List<Path> engineSpecs(Path modulesDir) {
        try (Stream<Path> walk = Files.walk(modulesDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .filter(MenuOpenSoundDriftTest::inGuiFolder)
                    .filter(MenuOpenSoundDriftTest::isEngineSpec)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static boolean inGuiFolder(Path conf) {
        Path parent = conf.getParent();
        return parent != null && parent.getFileName().toString().equals("gui");
    }

    private static boolean isEngineSpec(Path conf) {
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(conf).build().load();
            return !root.node("items").virtual();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
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
