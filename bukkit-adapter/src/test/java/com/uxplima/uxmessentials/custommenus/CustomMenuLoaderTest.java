package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.ArgumentSpec.ArgType;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
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

    private static final String WITH_COMMAND = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            command { name = "shop", aliases = ["store"], permission = "srv.shop", deny-message = "<red>nope", console = true }
            """;

    private static final String WITH_ARGUMENTS = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            command {
              name = "gift"
              usage = "/gift <target> <amount>"
              arguments = [
                { name = "target", type = "online-player" }
                { name = "amount", type = "int" }
                { name = "flavour", type = "bogus" }
              ]
            }
            """;

    private static final String WITH_GREEDY = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            command {
              name = "say"
              arguments = [ { name = "message", type = "string", greedy = true } ]
            }
            """;

    private static final String WITH_NON_LAST_GREEDY = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            command {
              name = "gift"
              arguments = [
                { name = "note", type = "string", greedy = true }
                { name = "amount", type = "int" }
              ]
            }
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
    void parsesTheCommandBlockIntoAnOpenCommandSpecKeyedByMenuId(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("catalog.conf"), WITH_COMMAND);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        CustomMenuLoader loader =
                new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), new RecordingLogger());

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        assertThat(result.loadedNames()).containsExactly("catalog");
        assertThat(result.openCommands()).containsOnlyKeys("catalog");
        OpenCommandSpec command = result.openCommands().get("catalog");
        assertThat(command.name()).isEqualTo("shop");
        assertThat(command.aliases()).containsExactly("store");
        assertThat(command.permission()).contains("srv.shop");
        assertThat(command.denyMessage()).contains("<red>nope");
        assertThat(command.consoleAllowed()).isTrue();
    }

    @Test
    void parsesOrderedTypedArgumentsAndUsageAndDefaultsAnUnknownTypeToString(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("gift.conf"), WITH_ARGUMENTS);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        RecordingLogger log = new RecordingLogger();
        CustomMenuLoader loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), log);

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        OpenCommandSpec command = result.openCommands().get("gift");
        assertThat(command.name()).isEqualTo("gift");
        assertThat(command.usage()).contains("/gift <target> <amount>");
        assertThat(command.arguments()).extracting(ArgumentSpec::name).containsExactly("target", "amount", "flavour");
        assertThat(command.arguments())
                .extracting(ArgumentSpec::type)
                .containsExactly(ArgType.ONLINE_PLAYER, ArgType.INT, ArgType.STRING);
        assertThat(log.warnings).anyMatch(line -> line.contains("unknown type") && line.contains("bogus"));
    }

    @Test
    void parsesAGreedyFlagOnTheLastStringArgument(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("say.conf"), WITH_GREEDY);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        CustomMenuLoader loader =
                new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), new RecordingLogger());

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        OpenCommandSpec command = result.openCommands().get("say");
        assertThat(command.arguments()).singleElement().satisfies(arg -> {
            assertThat(arg.name()).isEqualTo("message");
            assertThat(arg.type()).isEqualTo(ArgType.STRING);
            assertThat(arg.greedy()).isTrue();
        });
    }

    @Test
    void warnsWhenAGreedyFlagIsNotOnTheLastArgument(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("gift.conf"), WITH_NON_LAST_GREEDY);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        RecordingLogger log = new RecordingLogger();
        CustomMenuLoader loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), log);

        loader.loadFrom(dir);

        assertThat(log.warnings).anyMatch(line -> line.contains("greedy") && line.contains("note"));
    }

    @Test
    void aMenuWithoutACommandBlockContributesNoOpenCommand(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("good.conf"), GOOD);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        CustomMenuLoader loader =
                new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), new RecordingLogger());

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        assertThat(result.loadedNames()).containsExactly("good");
        assertThat(result.openCommands()).isEmpty();
    }

    @Test
    void patternsConfIsExcludedFromTheMenuSweepLikeOpenersConf(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("good.conf"), GOOD);
        Files.writeString(dir.resolve("patterns.conf"), """
                patterns { hub-button { material = "%mat%", name = "<gold>%label%" } }
                """);

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", c -> {});
        CustomMenuLoader loader =
                new CustomMenuLoader(new MenuSpecLoader(), bindings, newMenus(), new RecordingLogger());

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        assertThat(result.loaded()).isEqualTo(1);
        assertThat(result.loadedNames())
                .as("patterns.conf is a shared-templates file, not a menu, so it is not swept in")
                .containsExactly("good");
        assertThat(result.skipped()).doesNotContain("patterns");
    }

    @Test
    void aMenuReferencingAPatternStillLoadsWhenNoPatternsConfIsPresent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("shop.conf"), """
                rows = 1
                items { hub { slots = [0], pattern = "hub-button", vars { mat = "DIAMOND" } } }
                """);

        CustomMenuLoader loader =
                new CustomMenuLoader(new MenuSpecLoader(), new MenuBindings(), newMenus(), new RecordingLogger());

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);

        assertThat(result.loadedNames())
                .as("shared patterns are opt-in: a missing patterns.conf is the slice-A warn path, not a skip")
                .containsExactly("shop");
        assertThat(result.skipped()).isEmpty();
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
        return new Menus(mock(MenuRenderer.class), mock(Scheduler.class), new ListSourceRegistry());
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
