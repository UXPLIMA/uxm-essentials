package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockDetector;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockScreen;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The proof that the hybrid Bedrock renderer at the {@code Menus.open} choke-point works end to end: a real
 * {@link MenuSpecLoader} parses the spec, a real {@link Menus} wired with the live {@link MenuBindings} action
 * registry opens it, and a fake {@link BedrockDetector} / {@link BedrockScreen} stand in for the Cumulus/Floodgate
 * SDK, which is a {@code compileOnly} soft-depend absent from the test runtime. A Bedrock viewer gets a native
 * SimpleForm (a button list) instead of the chest, tapping a button runs that item's click actions, a Java viewer
 * keeps the chest byte-identically, and a chest-only menu stays on the chest even for a Bedrock viewer.
 */
class BedrockSimpleFormGoldenTest {

    private static final String TWO_ITEMS = """
            title = "My Menu"
            rows = 1
            items {
              alpha { slot = 0, material = DIAMOND, name = "Alpha" }
              beta  { slot = 1, material = EMERALD, name = "Beta", click { left { click = ["record:beta"] } } }
            }
            """;

    private static final String CHEST_ONLY = """
            title = "Storage"
            rows = 1
            chest-only = true
            items { a { slot = 0, material = CHEST, name = "A" } }
            """;

    private ServerMock server;
    private PlayerMock player;
    private MenuBindings bindings;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private MenuSpecLoader loader;
    private FakeBedrockDetector detector;
    private FakeBedrockScreen screen;
    private AtomicReference<String> captured;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        captured = new AtomicReference<>();

        bindings = new MenuBindings();
        bindings.action("record", ctx -> captured.set(ctx.arg()));
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        detector = new FakeBedrockDetector();
        screen = new FakeBedrockScreen();
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBedrockViewerGetsAFormNotAChestAndTappingAButtonRunsThatItemsActions() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("m", loader.parse(TWO_ITEMS));

        open(menus, "m");

        assertThat(menuOpen())
                .as("a Bedrock viewer is redirected to a form, so no chest MenuHolder is opened")
                .isFalse();
        assertThat(screen.title)
                .as("the form carries the menu's own title as plain text")
                .isEqualTo("My Menu");
        assertThat(screen.buttons)
                .as("one button per visible static item, in slot order, labelled with the item name")
                .containsExactly("Alpha", "Beta");

        screen.tap(1);

        assertThat(captured.get())
                .as("tapping Beta's button runs Beta's left-click actions through the shared action registry")
                .isEqualTo("beta");
    }

    @Test
    void aJavaViewerKeepsTheChestAndTheScreenIsNeverCalled() {
        detector.bedrock = false;
        Menus menus = engine();
        menus.registerSpec("m", loader.parse(TWO_ITEMS));

        open(menus, "m");

        assertThat(menuOpen())
                .as("a Java viewer opens the chest exactly as before")
                .isTrue();
        assertThat(screen.sent)
                .as("the Bedrock screen is never asked to send a form for a Java viewer")
                .isFalse();
    }

    @Test
    void aChestOnlyMenuStaysAChestEvenForABedrockViewer() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("s", loader.parse(CHEST_ONLY));

        open(menus, "s");

        assertThat(menuOpen())
                .as("a chest-only menu opts out of the form redirect, so a Bedrock viewer still gets the chest")
                .isTrue();
        assertThat(screen.sent).as("no form is sent for a chest-only menu").isFalse();
    }

    /** The production-shaped façade: action registry threaded in, plus the fake detector and screen. */
    private Menus engine() {
        return new Menus(
                renderer,
                scheduler,
                bindings.lists(),
                null,
                bindings.actions(),
                bindings.conditions(),
                null,
                detector,
                screen);
    }

    private void open(Menus menus, String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    /** Whether the viewer is looking at one of this engine's chest windows. A gated/redirected open never opens one. */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /** Records the last form it was asked to send and hands the test the select callback to invoke a tap with. */
    private static final class FakeBedrockScreen implements BedrockScreen {
        private boolean sent;
        private @Nullable String title;
        private List<String> buttons = List.of();
        private @Nullable IntConsumer onSelect;

        @Override
        public void sendSimpleForm(
                Player player, String title, @Nullable String content, List<String> buttons, IntConsumer onSelect) {
            this.sent = true;
            this.title = title;
            this.buttons = List.copyOf(buttons);
            this.onSelect = onSelect;
        }

        void tap(int index) {
            if (onSelect != null) {
                onSelect.accept(index);
            }
        }
    }

    /** A detector whose Bedrock answer the test flips per case; no Floodgate SDK involved. */
    private static final class FakeBedrockDetector implements BedrockDetector {
        private boolean bedrock;

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock;
        }
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
