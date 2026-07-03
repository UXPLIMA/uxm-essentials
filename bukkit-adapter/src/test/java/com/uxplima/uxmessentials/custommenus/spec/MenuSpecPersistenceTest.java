package com.uxplima.uxmessentials.custommenus.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plain-JUnit coverage of {@link MenuSpecPersistence}, the Bukkit-free save half of the editor spec service. A spec
 * whose refs are all registered saves to {@code menus/<name>.conf} and reloads to an equal model; a spec that names an
 * unregistered ref is rejected with the offending ids and nothing is written, so an editor can never persist a menu
 * the loader would only skip.
 */
class MenuSpecPersistenceTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();

    @TempDir
    Path menusDir;

    @Test
    void savesAValidSpecToAFileThatReloadsEqual() {
        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
        MenuSpec spec = loader.parse("""
                title = "<gold>Shop"
                rows = 1
                items { buy { slot = 0, material = EMERALD, name = "<green>Buy", click { left = ["close"] } } }
                """);

        MenuSpecPersistence.SaveResult result = persistence(bindings).save(menusDir, "shop", spec, null);

        assertThat(result.status()).isEqualTo(MenuSpecPersistence.Status.SAVED);
        assertThat(result.isSaved()).isTrue();
        Path file = menusDir.resolve("shop.conf");
        assertThat(Files.exists(file)).isTrue();
        assertThat(loader.parse(readString(file))).isEqualTo(spec);
    }

    @Test
    void rejectsAnUnregisteredRefWithoutWritingAFile() {
        // The 'ghost' action is never registered, so validation flags it and the write is refused.
        MenuBindings bindings = new MenuBindings();
        MenuSpec spec = loader.parse("""
                rows = 1
                items { x { slot = 0, material = STONE, click { left = ["ghost"] } } }
                """);

        MenuSpecPersistence.SaveResult result = persistence(bindings).save(menusDir, "broken", spec, null);

        assertThat(result.status()).isEqualTo(MenuSpecPersistence.Status.INVALID_REFS);
        assertThat(result.missingRefs()).containsExactly("ghost");
        assertThat(Files.exists(menusDir.resolve("broken.conf"))).isFalse();
    }

    private MenuSpecPersistence persistence(MenuBindings bindings) {
        return new MenuSpecPersistence(new MenuSpecWriter(), bindings, new NoopLogger());
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("failed to read " + file, failure);
        }
    }

    /** A logger double for the save service; these tests never assert on its output. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
