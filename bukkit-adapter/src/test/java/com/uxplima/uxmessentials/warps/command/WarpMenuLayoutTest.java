package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpMenuView;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpNotifier;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Proves the {@code /warp list} menu honours its injected {@link GuiLayout}: a three-row layout opens a 27-slot
 * menu, the unchanged default still opens 54 slots, and the title resolved from
 * {@link WarpsMessageKey#WARP_MENU_TITLE} is preserved across layouts.
 */
class WarpMenuLayoutTest {

    private static final String TITLE = WarpsMessageKey.WARP_MENU_TITLE.key();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        com.uxplima.uxmlib.gui.Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        com.uxplima.uxmlib.gui.Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void aThreeRowLayoutOpensATwentySevenSlotMenu() {
        openAndReturn(layout(3));

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(menu.getSize()).isEqualTo(27);
    }

    @Test
    void theDefaultLayoutStillOpensFiftyFourSlots() {
        openAndReturn(GuiLayout.paginatedDefault(Material.ENDER_PEARL));

        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(54);
    }

    @Test
    void theTitleStaysCatalogDrivenAcrossLayouts() {
        PaginatedGui gui = openAndReturn(layout(3));

        assertThat(gui.title()).isEqualTo(Component.text(TITLE));
    }

    private PaginatedGui openAndReturn(GuiLayout layout) {
        WarpMenuView view = new WarpMenuView(new KeyMessages(), new SyncScheduler(), useWarp(), layout);
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        view.open(player, viewer, warps());
        return (PaginatedGui) player.getOpenInventory().getTopInventory().getHolder();
    }

    private static GuiLayout layout(int rows) {
        return new GuiLayout(rows, Material.ENDER_PEARL, Material.ARROW, rows * 9 - 6, rows * 9 - 4, List.of());
    }

    private static List<Warp> warps() {
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
        return List.of(Warp.create(WarpName.of("spawn"), Position.of(world, 0, 64, 0), owner, Instant.now()));
    }

    private UseWarp useWarp() {
        Messages messages = new KeyMessages();
        WarpNotifier notifier = new WarpNotifier(messages, new NoSink());
        WarpRepository repository = new FakeRepository();
        WarpTeleporter teleporter = (who, warp) -> {};
        Permissions permissions = new AllowAllPermissions();
        WarpAccess access = new WarpAccess(permissions, Optional.<WarpEconomy>empty());
        return new UseWarp(repository, access, teleporter, notifier, pos -> true, permissions);
    }

    private static final class FakeRepository implements WarpRepository {
        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.empty();
        }

        @Override
        public List<Warp> all() {
            return warps();
        }

        @Override
        public boolean exists(WarpName name) {
            return false;
        }

        @Override
        public void save(Warp warp) {}

        @Override
        public void delete(WarpName name) {}

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
