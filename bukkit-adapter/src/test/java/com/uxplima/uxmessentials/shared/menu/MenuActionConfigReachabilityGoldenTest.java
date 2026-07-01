package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * The proof that a Phase-2 generic action written as an {@code id:value} token in a config is reachable when the
 * click fires. {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref#parse} is registry-blind:
 * it only splits an {@code id:value} token when the head is one of a few hardcoded prefixes, so a hyphenated v2
 * action such as {@code record-note:hello world} arrives at the runtime with the whole token as its id and would
 * miss the action registry. The real {@link MenuListener} closes that gap with a registry-aware split, so these
 * tests drive the whole config → open → click → effect path against the live listener rather than asserting the
 * parser in isolation.
 */
class MenuActionConfigReachabilityGoldenTest {

    private static final String NOTE_HOCON = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = ["record-note:hello world"] } }
            }
            """;

    private static final String COLON_VALUE_HOCON = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = ["record-note:a:b:c"] } }
            }
            """;

    private static final String FEATURE_HOCON = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = ["myfeature:go"] } }
            }
            """;

    private static final String MAP_FORM_HOCON = """
            rows = 1
            items {
              b { slot = 0, material = DIAMOND, name = "x", click { left = [ { do = "record-note:hi", chance = 100 } ] } }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private AtomicReference<String> captured;
    private AtomicBoolean featureFired;
    private AtomicReference<String> featureArg;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        captured = new AtomicReference<>();
        featureFired = new AtomicBoolean();
        featureArg = new AtomicReference<>("");

        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        bindings.action("record-note", ctx -> captured.set(ctx.arg()));
        bindings.action("myfeature:go", ctx -> {
            featureFired.set(true);
            featureArg.set(ctx.arg());
        });
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("note", loader.parse(NOTE_HOCON));
        menus.registerSpec("colon", loader.parse(COLON_VALUE_HOCON));
        menus.registerSpec("feature", loader.parse(FEATURE_HOCON));
        menus.registerSpec("map", loader.parse(MAP_FORM_HOCON));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aHyphenatedGenericActionWrittenAsIdValueRuns() {
        open("note");

        leftClick(0);

        assertThat(captured.get())
                .as("a config [record-note:hello world] token reaches the registered action with its value")
                .isEqualTo("hello world");
    }

    @Test
    void theRegistryAwareSplitStopsAtTheFirstColon() {
        open("colon");

        leftClick(0);

        assertThat(captured.get())
                .as("only the first colon splits the token, so the value keeps its own colons")
                .isEqualTo("a:b:c");
    }

    @Test
    void aWholeTokenFeatureRefResolvesWholeAndIsNotReSplit() {
        open("feature");

        leftClick(0);

        assertThat(featureFired.get())
                .as("a registered id that happens to contain a colon resolves as a whole token")
                .isTrue();
        assertThat(featureArg.get())
                .as("a whole-token feature ref carries no split-off value")
                .isEmpty();
    }

    @Test
    void aMapFormActionWithModifiersStillResolvesAndRuns() {
        open("map");

        leftClick(0);

        assertThat(captured.get())
                .as("the map form's modifiers survive the re-split and the action still fires with its value")
                .isEqualTo("hi");
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    /** Fire a left click at {@code slot} of the top (menu) inventory through the live listener. */
    private void leftClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
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
