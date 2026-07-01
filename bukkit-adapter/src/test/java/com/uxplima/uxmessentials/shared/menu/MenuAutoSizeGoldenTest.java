package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
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
 * End-to-end proof of the two loader conveniences that need a live open to be convincing: a rows-less chest
 * auto-sizes its window to the highest declared slot, and the {@code update-interval} key arms the timed refresh.
 * A real {@link MenuSpecLoader} parses each spec and a real {@link Menus} opens it, so the assertions land on the
 * live top inventory's size (auto-size) and on a task actually scheduled through the engine's own refresh arming
 * (update-interval) rather than on the parsed model alone.
 */
class MenuAutoSizeGoldenTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingScheduler scheduler;
    private Menus menus;
    private MenuSpecLoader loader;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");

        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new RecordingScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aNoRowsChestAutoSizesToFitItsHighestSlotOnOpen() {
        Inventory inv = open("shop", """
                items { pick { slot = 20, material = DIAMOND, name = "x" } }
                """);

        assertThat(inv.getType()).isEqualTo(InventoryType.CHEST);
        assertThat(inv.getSize())
                .as("a slot in the third row auto-sizes the chest to three rows")
                .isEqualTo(27);
        assertThat(inv.getItem(20)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void theUpdateIntervalKeyArmsTheTimedRefreshOnOpen() {
        open("live", """
                update-interval = 20
                items { pick { slot = 0, material = DIAMOND, name = "x" } }
                """);

        assertThat(scheduler.repeats)
                .as("update-interval enables the refresh, so exactly one repeating task is armed")
                .isEqualTo(1);
    }

    @Test
    void aStaticMenuArmsNoRefreshTask() {
        open("static", """
                items { pick { slot = 0, material = DIAMOND, name = "x" } }
                """);

        assertThat(scheduler.repeats)
                .as("with neither update-interval nor a refresh block, a static menu schedules nothing")
                .isZero();
    }

    private Inventory open(String id, String hocon) {
        menus.registerSpec(id, loader.parse(hocon));
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), id, null);
        return player.getOpenInventory().getTopInventory();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs immediate work inline so the open completes in the test thread, and counts armed repeating tasks. */
    private static final class RecordingScheduler implements Scheduler {
        private int repeats = 0;

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
            repeats++;
            return () -> {};
        }
    }
}
