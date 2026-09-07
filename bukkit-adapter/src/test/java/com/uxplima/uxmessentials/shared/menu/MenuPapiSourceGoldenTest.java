package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineScheduler;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.MenusMenuPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderResolver;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.LastMenu;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end coverage of the menu engine exposed as a PlaceholderAPI source. A real {@link Menus} opens a spec for
 * a {@link PlayerMock}; a {@link MenusMenuPlaceholders} reads that same façade; and the assertions go through the
 * live {@link PlaceholderResolver}, exactly as the expansion would. It pins the six {@code menu_*} keys against the
 * live window and the reopen history, including the {@code opened}-vs-{@code last} split once a menu closes.
 */
class MenuPapiSourceGoldenTest {

    private static final String BROWSE_HOCON = """
            rows = 3
            items { b { slot = 0, material = ARROW, name = "browse-item" } }
            """;

    private static final String ARG_HOCON = """
            rows = 1
            items { b { slot = 0, material = PAPER, name = "arg-item" } }
            """;

    private ServerMock server;
    private PlayerMock player;
    private LastMenu lastMenu;
    private Menus menus;
    private PlaceholderResolver resolver;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");

        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, ThemeFile::shippedTheme, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuSpecLoader loader = new MenuSpecLoader();
        lastMenu = new LastMenu();
        menus = new Menus(renderer, EngineScheduler.of(scheduler), bindings.lists(), null, null, null, lastMenu);
        menus.registerSpec("browse", loader.parse(BROWSE_HOCON));
        menus.registerSpec("argmenu", loader.parse(ARG_HOCON));

        PlaceholderContexts contexts = PlaceholderContexts.builder()
                .menu(new MenusMenuPlaceholders(menus))
                .build();
        resolver = new PlaceholderResolver(contexts);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOpenMenuExposesItsIdPageAndRows() {
        menus.open(player, "browse", null);

        assertThat(resolve("menu_is_in_menu")).contains("yes");
        assertThat(resolve("menu_opened")).contains("browse");
        assertThat(resolve("menu_page")).contains("1");
        assertThat(resolve("menu_rows")).contains("3");
    }

    @Test
    void aTypedArgumentTheMenuWasOpenedWithIsReadableAsASource() {
        menus.open(player, "argmenu", null, 0, Map.of("target", "Steve"));

        assertThat(resolve("menu_argument_target")).contains("Steve");
        assertThat(resolve("menu_argument_absent")).contains("-");
    }

    @Test
    void aPlayerInNoMenuReadsNotInMenu() {
        assertThat(resolve("menu_is_in_menu")).contains("no");
        assertThat(resolve("menu_opened")).contains("-");
        assertThat(resolve("menu_page")).contains("-");
    }

    @Test
    void lastPersistsAfterTheMenuCloses() {
        menus.open(player, "browse", null);
        player.closeInventory();

        // opened tracks the live window and clears on close; last is the reopen history and survives the close.
        assertThat(resolve("menu_is_in_menu")).contains("no");
        assertThat(resolve("menu_opened")).contains("-");
        assertThat(resolve("menu_last")).contains("browse");
    }

    private Optional<String> resolve(String key) {
        return resolver.resolve(ref(), true, key);
    }

    private PlayerRef ref() {
        return BukkitRefs.toRef(player);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
