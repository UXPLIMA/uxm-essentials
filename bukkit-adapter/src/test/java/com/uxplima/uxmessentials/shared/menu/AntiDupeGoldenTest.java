package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuItemMark;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuAntiDupeListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
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
 * The end-to-end proof for the anti-dupe safety net. Two halves run against the real engine and the real
 * {@link MenuAntiDupeListener}: every tile the engine renders is marked (so an escaped copy is identifiable), and a
 * marked copy found in a player's real inventory is stripped on close and on join while genuine items are left
 * untouched. The strip only ever removes {@link MenuItemMark marked} items, so a player who holds only real items keeps
 * them across both events.
 */
class AntiDupeGoldenTest {

    private static final String MENU_HOCON = """
            rows = 1
            items { x { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        log = new RecordingLogger();

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        menus = engine.menus();
        menus.registerSpec("marked-menu", new MenuSpecLoader().parse(MENU_HOCON));
        server.getPluginManager().registerEvents(new MenuAntiDupeListener(log), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyRenderedTileCarriesTheMark() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "marked-menu", null);

        ItemStack tile = player.getOpenInventory().getTopInventory().getItem(0);
        assertThat(MenuItemMark.isMarked(tile))
                .as("every tile the engine renders is marked so an escaped copy is strippable")
                .isTrue();
    }

    @Test
    void aCloseStripsAMarkedCopyButLeavesGenuineItems() {
        player.getInventory().setItem(0, MenuItemMark.mark(new ItemStack(Material.GOLD_INGOT)));
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND));

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        assertThat(player.getInventory().getItem(0))
                .as("the escaped marked copy is stripped on close")
                .isNull();
        assertThat(player.getInventory().getItem(1))
                .as("a genuine item beside it is untouched")
                .isEqualTo(new ItemStack(Material.DIAMOND));
        assertThat(log.debugs)
                .as("stripping an abnormal item logs one operator diagnostic")
                .hasSize(1);
    }

    @Test
    void aJoinStripsAMarkedCopyThatPersistedAcrossARestart() {
        player.getInventory().setItem(0, MenuItemMark.mark(new ItemStack(Material.GOLD_INGOT)));
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND));

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertThat(player.getInventory().getItem(0))
                .as("a marked copy saved to the player file at shutdown is stripped on join")
                .isNull();
        assertThat(player.getInventory().getItem(1))
                .as("a genuine item beside it is untouched")
                .isEqualTo(new ItemStack(Material.DIAMOND));
    }

    @Test
    void aRealInventoryOfGenuineItemsIsUntouchedByEitherSweep() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        player.getInventory().setItem(1, new ItemStack(Material.BREAD));

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertThat(player.getInventory().getItem(0))
                .as("a genuine stack is never stripped by the close sweep")
                .isEqualTo(new ItemStack(Material.DIAMOND, 3));
        assertThat(player.getInventory().getItem(1))
                .as("a genuine stack is never stripped by the join sweep")
                .isEqualTo(new ItemStack(Material.BREAD));
        assertThat(log.debugs).as("a no-op sweep logs nothing").isEmpty();
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> debugs = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {
            debugs.add(message);
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
