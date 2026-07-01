package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 * The end-to-end proof for the two Phase-7 click features: the drop/double-click gestures and the anti-spam
 * cooldown. The real {@link MenuSpecLoader} reads the config, the live {@link MenuListener} maps the Bukkit
 * {@link ClickType} to a gesture and throttles the window, and a recording {@code record} action captures which
 * gesture fired and how often — so these drive config → open → click → effect. The cooldown listener is handed a
 * clock it advances by hand so the window is deterministic; the clock base sits above the cooldown so the first
 * click (measured against the holder's zero stamp) always passes, exactly as the real millisecond clock does in
 * production where {@code System.currentTimeMillis()} dwarfs any window.
 */
class ClickKindCooldownGoldenTest {

    private static final String GESTURES = """
            rows = 1
            items { x { slot = 0, material = DIAMOND, name = "x", click {
              left = ["record:left"]
              drop = ["record:drop"]
              double_click = ["record:double"]
            } } }
            """;

    private static final String COOLED = """
            rows = 1
            click-cooldown = 500
            items { x { slot = 0, material = DIAMOND, name = "x", click { left = ["record:hit"] } } }
            """;

    private static final String PLAIN = """
            rows = 1
            items { x { slot = 0, material = DIAMOND, name = "x", click { left = ["record:hit"] } } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private MenuRenderer renderer;
    private MenuBindings bindings;
    private Scheduler scheduler;
    private List<String> notes;

    /** The hand-advanced clock backing the cooldown listener; starts above the window so the first click passes. */
    private long clockNow = 1_000L;

    /** The clock reading the throttle measures against; each test advances {@link #clockNow} between clicks. */
    private long now() {
        return clockNow;
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        bindings = new MenuBindings();
        bindings.action("record", ctx -> notes.add(ctx.arg()));

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("gestures", loader.parse(GESTURES));
        menus.registerSpec("cooled", loader.parse(COOLED));
        menus.registerSpec("plain", loader.parse(PLAIN));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDropClickFiresTheDropGestureAndNotTheLeftGesture() {
        install(0L);
        open("gestures");

        click(0, ClickType.DROP);

        assertThat(notes).as("a drop gesture runs only its own actions").containsExactly("drop");
    }

    @Test
    void aDoubleClickFiresTheDoubleClickGesture() {
        install(0L);
        open("gestures");

        click(0, ClickType.DOUBLE_CLICK);

        assertThat(notes).as("the double-click gesture binds independently").containsExactly("double");
    }

    @Test
    void aLeftClickDoesNotFireTheDropGesture() {
        install(0L);
        open("gestures");

        click(0, ClickType.LEFT);

        assertThat(notes)
                .as("a plain left click runs the left gesture, never the drop one")
                .containsExactly("left");
    }

    @Test
    void aPerMenuCooldownBlocksASecondClickInsideTheWindowButAllowsItAfter() {
        install(0L);
        open("cooled");

        clockNow = 1_000L;
        click(0, ClickType.LEFT);
        clockNow = 1_100L;
        click(0, ClickType.LEFT);
        clockNow = 1_600L;
        click(0, ClickType.LEFT);

        assertThat(notes)
                .as("t=0 fires, t=100 is inside the 500ms window and swallowed, t=600 is past it and fires")
                .containsExactly("hit", "hit");
    }

    @Test
    void theGlobalDefaultThrottlesAMenuThatSetsNoCooldownOfItsOwn() {
        install(500L);
        open("plain");

        clockNow = 1_000L;
        click(0, ClickType.LEFT);
        clockNow = 1_100L;
        click(0, ClickType.LEFT);
        clockNow = 1_600L;
        click(0, ClickType.LEFT);

        assertThat(notes)
                .as("a menu with no click-cooldown inherits the 500ms server-wide floor")
                .containsExactly("hit", "hit");
    }

    @Test
    void withNoWindowAtAllEveryClickFires() {
        install(0L);
        open("plain");

        clockNow = 1_000L;
        click(0, ClickType.LEFT);
        clockNow = 1_001L;
        click(0, ClickType.LEFT);
        clockNow = 1_002L;
        click(0, ClickType.LEFT);

        assertThat(notes)
                .as("global 0 and per-menu 0 mean no throttle, so all three clicks fire")
                .containsExactly("hit", "hit", "hit");
    }

    private void install(long defaultCooldownMs) {
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                null,
                null,
                null,
                defaultCooldownMs,
                this::now);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    private void click(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
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
