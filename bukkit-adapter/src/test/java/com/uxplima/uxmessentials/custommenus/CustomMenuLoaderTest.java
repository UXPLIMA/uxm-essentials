package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomMenuLoaderTest {

    private static final String GOOD = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private static final String BAD = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["nope:x"] } } }
            """;

    @Test
    void loadsValidSpecAndSkipsInvalidOnes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("good.conf"), GOOD);
        Files.writeString(dir.resolve("bad.conf"), BAD);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        RecordingLogger log = new RecordingLogger();
        CustomMenuLoader loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), log);

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.loadedNames()).containsExactly("good");
        assertThat(result.skipped()).containsExactly("bad");
        assertThat(log.warnings).anyMatch(line -> line.contains("bad"));
    }

    @Test
    void missingDirIsEmptyResult(@TempDir Path dir) {
        CustomMenuLoader loader =
                new CustomMenuLoader(new MenuSpecLoader(), new MenuBindings(), newMenus(), new RecordingLogger());

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir.resolve("absent"));

        assertThat(result.loaded()).isZero();
        assertThat(result.skipped()).isEmpty();
    }

    private static Menus newMenus() {
        return new Menus(
                mock(MenuRenderer.class), mock(GuiText.class), mock(Scheduler.class), new ListSourceRegistry());
    }

    /** Captures the SLF4J-style lines the loader emits so the test can assert on what an operator would read. */
    private static final class RecordingLogger implements Logger {

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(render(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String render(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                out = out.replaceFirst("\\{}", String.valueOf(arg));
            }
            return out;
        }
    }
}
