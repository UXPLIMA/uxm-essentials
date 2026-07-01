package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end coverage of the dynamic back-stack through the real {@link MenuListener}. Two specs each carry a
 * {@code back}-bound button; opening one then the other and clicking back reopens the first, a second back from the
 * root closes, a repeated open of the same menu is de-duplicated so back still reaches the true previous, and a
 * {@code back} on an engine wired without a history tracker simply closes.
 */
class BackStackGoldenTest {

    private static final String A_HOCON = """
            rows = 1
            items { b { slot = 0, material = ARROW, name = "A-back", click { left = ["back"] } } }
            """;

    private static final String B_HOCON = """
            rows = 1
            items { b { slot = 0, material = PAPER, name = "B-back", click { left = ["back"] } } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuBindings bindings;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private MenuSpecLoader loader;
    private LastMenu lastMenu;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");

        GuiText guiText = new GuiText(new KeyMessages());
        bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        loader = new MenuSpecLoader();
        lastMenu = new LastMenu();
        menus = new Menus(renderer, scheduler, bindings.lists(), null, null, null, lastMenu);
        MenuVocabulary.registerActions(bindings, menus, true, new RecordingLogger());
        menus.registerSpec("a", loader.parse(A_HOCON));
        menus.registerSpec("b", loader.parse(B_HOCON));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void clickingBackReopensThePreviousMenu() {
        open("a");
        open("b");
        assertThat(specId()).isEqualTo("b");

        leftClick(0); // back → reopen a

        assertThat(specId()).isEqualTo("a");
    }

    @Test
    void backFromTheRootMenuClosesTheWindow() {
        open("a");
        open("b");

        leftClick(0); // back → a
        assertThat(specId()).isEqualTo("a");

        leftClick(0); // back from the root → nothing beneath → close

        assertThat(menuIsOpen()).isFalse();
    }

    @Test
    void openingTheSameMenuTwiceDoesNotStackADuplicate() {
        open("a");
        open("a"); // a refresh / re-open of the same menu must not stack a second entry
        open("b");

        leftClick(0); // back from b → a
        assertThat(specId()).isEqualTo("a");

        leftClick(0); // the duplicate a was de-duplicated, so this is the root → close

        assertThat(menuIsOpen()).isFalse();
    }

    @Test
    void backOnAnEngineWiredWithoutAHistoryJustCloses() {
        Menus plain = new Menus(renderer, scheduler, bindings.lists());
        plain.registerSpec("a", loader.parse(A_HOCON));
        plain.open(ref(), "a", null);
        assertThat(menuIsOpen()).isTrue();

        plain.back(ref());

        assertThat(menuIsOpen()).isFalse();
    }

    private void open(String specId) {
        menus.open(ref(), specId, null);
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private void leftClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
    }

    private String specId() {
        var top = player.getOpenInventory().getTopInventory();
        if (top == null || !(top.getHolder() instanceof MenuHolder holder)) {
            return "";
        }
        return holder.specId();
    }

    private boolean menuIsOpen() {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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
