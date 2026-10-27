package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The background of every shipped window is the house filler: a grey pane that shows no tooltip and has no name.
 *
 * <p>Most windows filled with black panes named {@code ""}. The colour was not the house one, and a pane with a name,
 * even a blank one, still opens an empty box under the cursor; an empty name makes the client print the material's
 * own English name instead. A decorative pane is one with no click, no lore and no words in its name: a pane that does
 * something or says something is a button and keeps its own look.
 */
class MenuFillerDriftTest {

    private static final String PANE = "_STAINED_GLASS_PANE";

    private static final String FILLER = "GRAY_STAINED_GLASS_PANE";

    @Test
    void everyShippedBackgroundPaneIsTheHouseFiller() {
        Path modulesDir = repoRoot().resolve("bukkit-adapter/src/main/resources/modules");
        if (!Files.isDirectory(modulesDir)) {
            return;
        }

        MenuSpecLoader loader = new MenuSpecLoader();
        List<String> wrong = new ArrayList<>();
        for (Path conf : engineSpecs(modulesDir)) {
            MenuSpec spec = loader.load(conf);
            for (Map.Entry<String, MenuItemSpec> entry : spec.items().entrySet()) {
                MenuItemSpec item = entry.getValue();
                if (isBackground(item) && !isHouseFiller(item)) {
                    wrong.add(modulesDir.relativize(conf) + " " + entry.getKey() + " (" + item.material() + ", name \""
                            + item.name() + "\", hide-tooltip "
                            + item.decor().meta().components().hideTooltip().orElse(false) + ")");
                }
            }
        }

        assertThat(wrong)
                .as("background panes that are not GRAY_STAINED_GLASS_PANE with decor { hide-tooltip = true } and no "
                        + "name (see docs/14-ui-style.md)")
                .isEmpty();
    }

    @Test
    void everyLayoutFillerKeyNamesTheGreyPane() {
        Path modulesDir = repoRoot().resolve("bukkit-adapter/src/main/resources/modules");
        if (!Files.isDirectory(modulesDir)) {
            return;
        }

        List<String> wrong = new ArrayList<>();
        for (Path conf : confs(modulesDir)) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                    load(conf).childrenMap().entrySet()) {
                String key = String.valueOf(entry.getKey());
                String value = entry.getValue().getString();
                if ((key.equals("filler") || key.endsWith("-filler"))
                        && value != null
                        && !FILLER.equalsIgnoreCase(value)) {
                    wrong.add(modulesDir.relativize(conf) + " " + key + " = " + value);
                }
            }
        }

        assertThat(wrong)
                .as("layout filler keys that do not name GRAY_STAINED_GLASS_PANE")
                .isEmpty();
    }

    @Test
    void noCodeDefaultFillsWithABlackPane() {
        Path mainDir = repoRoot().resolve("bukkit-adapter/src/main/java");
        if (!Files.isDirectory(mainDir)) {
            return;
        }

        List<String> wrong = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainDir)) {
            for (Path source :
                    walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                if (Files.readString(source).contains("BLACK_STAINED_GLASS_PANE")) {
                    wrong.add(mainDir.relativize(source).toString());
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        assertThat(wrong)
                .as("code defaults that fill a window with black panes instead of the house filler")
                .isEmpty();
    }

    private static boolean isBackground(MenuItemSpec item) {
        return item.material().toUpperCase(Locale.ROOT).endsWith(PANE)
                && !item.click().hasAnyAction()
                && item.lore().isEmpty()
                && item.name().isBlank();
    }

    private static boolean isHouseFiller(MenuItemSpec item) {
        return FILLER.equalsIgnoreCase(item.material())
                && item.name().isEmpty()
                && item.decor().meta().components().hideTooltip().equals(Optional.of(true));
    }

    private static List<Path> confs(Path modulesDir) {
        try (Stream<Path> walk = Files.walk(modulesDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static ConfigurationNode load(Path conf) {
        try {
            return HoconConfigurationLoader.builder().path(conf).build().load();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Every engine spec bundled under a module's {@code gui/} folder, told apart from a layout conf by its items. */
    private static List<Path> engineSpecs(Path modulesDir) {
        try (Stream<Path> walk = Files.walk(modulesDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .filter(MenuFillerDriftTest::inGuiFolder)
                    .filter(MenuFillerDriftTest::isEngineSpec)
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
        return !load(conf).node("items").virtual();
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
