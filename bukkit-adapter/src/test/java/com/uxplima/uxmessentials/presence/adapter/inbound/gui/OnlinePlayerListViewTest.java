package com.uxplima.uxmessentials.presence.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link OnlinePlayerListView}: the read-only online-roster head grid. Given a
 * pre-snapshotted roster it draws one player head per entry into the paginated content slots; an empty roster
 * opens the one-row empty-state title instead of a grid. The scheduler is a synchronous double so the
 * entity-bound build runs inline, and uxmLib's menu listener is installed against a mock plugin.
 */
class OnlinePlayerListViewTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private OnlinePlayerListView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        view = new OnlinePlayerListView(
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void rendersOneHeadPerRosterEntry() {
        List<OnlinePlayerListView.Entry> roster = List.of(
                new OnlinePlayerListView.Entry(UUID.randomUUID(), "Alice", "world", "Online"),
                new OnlinePlayerListView.Entry(UUID.randomUUID(), "Bob", "world", "Online"));

        view.open(player, viewer, roster);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(2)).isNull();
    }

    @Test
    void anEmptyRosterOpensTheEmptyStateMenu() {
        view.open(player, viewer, List.of());

        Inventory inv = player.getOpenInventory().getTopInventory();
        // The empty-state menu is a one-row filler menu, never the paginated head grid.
        assertThat(inv.getHolder()).isNotInstanceOf(PaginatedGui.class);
        assertThat(inv.getSize()).isEqualTo(9);
    }

    /** Resolves a key to its own string so the view's rendering never needs a real catalog. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduler hop inline, as the production schedulers would after their marshal. */
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
