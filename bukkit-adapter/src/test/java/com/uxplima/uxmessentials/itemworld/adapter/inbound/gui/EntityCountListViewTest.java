package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
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
 * MockBukkit coverage of {@link EntityCountListView}: the read-only nearby-entity tally grid. Given a
 * pre-sorted tally it draws one spawn-egg icon per type into the paginated content slots, falls back to a
 * generic icon for a type that has no spawn egg (without throwing), and opens the one-row empty-state title for
 * an empty tally. The scheduler is a synchronous double so the entity-bound build runs inline.
 */
class EntityCountListViewTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private EntityCountListView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        view = new EntityCountListView(
                new GuiText(new KeyMessages()), new SyncScheduler(), EntityListLayout.paginatedDefault(Material.EGG));
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void rendersASpawnEggPerType() {
        List<EntityCountListView.Entry> tally = List.of(
                new EntityCountListView.Entry(EntityType.ZOMBIE, 7), new EntityCountListView.Entry(EntityType.COW, 3));

        view.open(player, viewer, tally, 64);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.ZOMBIE_SPAWN_EGG);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.COW_SPAWN_EGG);
    }

    @Test
    void aTypeWithNoSpawnEggFallsBackWithoutThrowing() {
        // ITEM (a dropped item-stack entity) has no _SPAWN_EGG material — the icon mapping must fall back, not throw.
        List<EntityCountListView.Entry> tally = List.of(new EntityCountListView.Entry(EntityType.ITEM, 12));

        assertThatCode(() -> view.open(player, viewer, tally, 64)).doesNotThrowAnyException();

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(0)).isNotNull();
        assertThat(inv.getItem(0).getType()).isNotEqualTo(Material.AIR);
    }

    @Test
    void anEmptyTallyOpensTheEmptyStateMenu() {
        view.open(player, viewer, List.of(), 64);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isNotInstanceOf(PaginatedGui.class);
        assertThat(inv.getSize()).isEqualTo(9);
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
