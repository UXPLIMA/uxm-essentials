package com.uxplima.uxmessentials.shared.menu.vocab;

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

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ConfirmOpener;
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
 * MockBukkit coverage of the {@code confirm:} menu step. Unlike {@code input:} it splits no remaining chain: its
 * two branches carry everything that follows the decision, so the dispatcher hands them to a confirm opener. A
 * {@link RecordingConfirm} stands in for the engine's confirm window so the test drives the accept and decline by
 * hand, proving the {@code yes} refs run on accept and the {@code no} refs on decline. The chain is loaded from
 * HOCON and opened through the real {@link Menus} path.
 */
class ConfirmActionTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private MenuBindings bindings;
    private RecordingConfirm confirm;
    private List<String> captured;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        captured = new ArrayList<>();
        bindings = new MenuBindings();
        bindings.action("capture", ctx -> captured.add(ctx.arg()));
        Scheduler scheduler = new SyncScheduler();
        ItemRenderer itemRenderer = new ItemRenderer(new GuiText(new KeyMessages()), bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists());
        confirm = new RecordingConfirm();
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                null,
                null,
                confirm,
                0L,
                System::currentTimeMillis,
                new PagedListSourceRegistry(),
                null);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acceptingTheConfirmRunsTheYesRefsAndNotTheNoRefs() {
        openDeleter();
        rightClick();

        assertThat(confirm.opens).as("the click opened the confirm window").isEqualTo(1);
        assertThat(captured).as("nothing runs until the viewer decides").isEmpty();

        confirm.accept();

        assertThat(captured).as("accepting ran the yes refs, not the no refs").containsExactly("YES");
    }

    @Test
    void decliningTheConfirmRunsTheNoRefsAndNotTheYesRefs() {
        openDeleter();
        rightClick();

        confirm.decline();

        assertThat(captured).as("declining ran the no refs, not the yes refs").containsExactly("NO");
    }

    private void openDeleter() {
        menus.registerSpec("menu", new MenuSpecLoader().parse("""
                                rows = 1
                                items {
                                  deleter { slot = 0, material = DIAMOND, name = "x", click {
                                    right = { do = "confirm:test.delete", title = "@test.title",
                                              yes = ["capture:YES"], no = ["capture:NO"] }
                                  } }
                                }
                                """));
        menus.open(viewer, "menu", null);
    }

    private void rightClick() {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A synchronous stand-in for the engine's confirm window: it records the two decisions so the test fires one. */
    private static final class RecordingConfirm implements ConfirmOpener {
        int opens;

        @Nullable Runnable onYes;

        @Nullable Runnable onNo;

        @Override
        public void openConfirm(PlayerRef viewer, Component title, Runnable onYes, Runnable onNo) {
            this.opens++;
            this.onYes = onYes;
            this.onNo = onNo;
        }

        void accept() {
            java.util.Objects.requireNonNull(onYes, "onYes").run();
        }

        void decline() {
            java.util.Objects.requireNonNull(onNo, "onNo").run();
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
    }
}
