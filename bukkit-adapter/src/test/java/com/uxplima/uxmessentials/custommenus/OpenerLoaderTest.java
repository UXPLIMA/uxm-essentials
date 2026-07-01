package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;

import com.uxplima.uxmessentials.custommenus.adapter.OpenerLoader;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Coverage of {@link OpenerLoader}: an {@code openers.conf} parses into {@link OpenerSpec}s (menu / item / slot /
 * give-on-join), a bad entry (unknown material, unknown menu) is skipped while the rest still load, a missing file
 * yields no openers, and give-on-join defaults to {@link GiveOnJoin#NEVER}. Runs under MockBukkit for
 * {@code Material.matchMaterial}.
 */
class OpenerLoaderTest {

    private static final Set<String> KNOWN = Set.of("hub", "shop");

    private OpenerLoader loader;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        loader = new OpenerLoader(new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void parsesTheMenuItemSlotAndGiveOnJoin(@TempDir Path dir) throws Exception {
        Path file = write(dir, """
                openers = [
                  {
                    menu = "hub"
                    item { material = "COMPASS", name = "<gold>Server Menu", lore = ["<gray>Right-click to open"] }
                    slot = 4
                    give-on-join = "first"
                  }
                ]
                """);

        List<OpenerSpec> openers = loader.loadFrom(file, KNOWN);

        assertThat(openers).hasSize(1);
        OpenerSpec opener = openers.getFirst();
        assertThat(opener.menu()).isEqualTo("hub");
        assertThat(opener.item().material()).isEqualTo(Material.COMPASS);
        assertThat(opener.item().name()).isEqualTo("<gold>Server Menu");
        assertThat(opener.item().lore()).containsExactly("<gray>Right-click to open");
        assertThat(opener.slot()).isEqualTo(4);
        assertThat(opener.giveOnJoin()).isEqualTo(GiveOnJoin.FIRST);
    }

    @Test
    void skipsAnEntryWithAnUnknownMaterialButLoadsTheRest(@TempDir Path dir) throws Exception {
        Path file = write(dir, """
                openers = [
                  { menu = "hub", item { material = "NOT_A_REAL_ITEM" } },
                  { menu = "shop", item { material = "CHEST" }, give-on-join = "always" }
                ]
                """);

        List<OpenerSpec> openers = loader.loadFrom(file, KNOWN);

        assertThat(openers).hasSize(1);
        assertThat(openers.getFirst().menu()).isEqualTo("shop");
        assertThat(openers.getFirst().giveOnJoin()).isEqualTo(GiveOnJoin.ALWAYS);
    }

    @Test
    void skipsAnEntryNamingAnUnknownMenu(@TempDir Path dir) throws Exception {
        Path file = write(dir, "openers = [ { menu = \"ghost\", item { material = \"COMPASS\" } } ]\n");

        assertThat(loader.loadFrom(file, KNOWN)).isEmpty();
    }

    @Test
    void giveOnJoinDefaultsToNeverWhenAbsent(@TempDir Path dir) throws Exception {
        Path file = write(dir, "openers = [ { menu = \"hub\", item { material = \"COMPASS\" } } ]\n");

        List<OpenerSpec> openers = loader.loadFrom(file, KNOWN);

        assertThat(openers).hasSize(1);
        assertThat(openers.getFirst().giveOnJoin()).isEqualTo(GiveOnJoin.NEVER);
        assertThat(openers.getFirst().slot()).isEqualTo(-1);
    }

    @Test
    void aMissingFileYieldsNoOpeners(@TempDir Path dir) {
        assertThat(loader.loadFrom(dir.resolve("openers.conf"), KNOWN)).isEmpty();
    }

    @Test
    void loadConfigParsesBothOpenersAndTheSwapMenu(@TempDir Path dir) throws Exception {
        Path file = write(dir, """
                swap-offhand-menu = "hub"
                openers = [
                  { menu = "shop", item { material = "CHEST" }, give-on-join = "always" }
                ]
                """);

        OpenerLoader.OpenerConfig config = loader.loadConfig(file, KNOWN);

        assertThat(config.openers()).hasSize(1);
        assertThat(config.openers().getFirst().menu()).isEqualTo("shop");
        assertThat(config.swapMenu()).contains("hub");
    }

    @Test
    void loadConfigWithoutASwapKeyLeavesTheSwapMenuEmpty(@TempDir Path dir) throws Exception {
        Path file = write(dir, "openers = [ { menu = \"hub\", item { material = \"COMPASS\" } } ]\n");

        OpenerLoader.OpenerConfig config = loader.loadConfig(file, KNOWN);

        assertThat(config.openers()).hasSize(1);
        assertThat(config.swapMenu()).isEmpty();
    }

    @Test
    void loadConfigDropsASwapMenuNamingAnUnknownMenu(@TempDir Path dir) throws Exception {
        Path file = write(dir, "swap-offhand-menu = \"ghost\"\n");

        assertThat(loader.loadConfig(file, KNOWN).swapMenu()).isEmpty();
    }

    @Test
    void loadConfigDropsABlankSwapMenu(@TempDir Path dir) throws Exception {
        Path file = write(dir, "swap-offhand-menu = \"\"\n");

        assertThat(loader.loadConfig(file, KNOWN).swapMenu()).isEmpty();
    }

    @Test
    void loadConfigOnAMissingFileIsEmpty(@TempDir Path dir) {
        OpenerLoader.OpenerConfig config = loader.loadConfig(dir.resolve("openers.conf"), KNOWN);

        assertThat(config.openers()).isEmpty();
        assertThat(config.swapMenu()).isEmpty();
    }

    private static Path write(Path dir, String content) throws Exception {
        Path file = dir.resolve("openers.conf");
        Files.writeString(file, content);
        return file;
    }

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
