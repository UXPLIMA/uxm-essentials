package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link RecipeGridView}: the fixed crafting-grid menu. Given a nine-cell ingredient
 * grid and a result it lays the ingredients into the grid slots and the result into its own slot; a null cell
 * renders as filler; the empty-state path opens the one-row no-recipe title. The scheduler is a synchronous
 * double so the entity-bound build runs inline.
 */
class RecipeGridViewTest {

    // The grid + result slots the view pins (kept in sync with RecipeGridView's private layout).
    private static final int FIRST_GRID_SLOT = 0;
    private static final int RESULT_SLOT = 15;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private RecipeGridView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        view = new RecipeGridView(
                new GuiText(new KeyMessages()), new SyncScheduler(), Material.BLACK_STAINED_GLASS_PANE);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void laysGridCellsAndResultIntoTheirSlots() {
        // A boat-like recipe: planks across the top grid row, the rest empty.
        List<@Nullable Material> grid =
                Arrays.asList(Material.OAK_PLANKS, null, Material.OAK_PLANKS, null, null, null, null, null, null);

        view.open(player, viewer, grid, Material.OAK_BOAT);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(SimpleGui.class);
        assertThat(inv.getItem(FIRST_GRID_SLOT).getType()).isEqualTo(Material.OAK_PLANKS);
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.OAK_PLANKS);
        // An empty cell renders as the filler material, never the ingredient.
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        assertThat(inv.getItem(RESULT_SLOT).getType()).isEqualTo(Material.OAK_BOAT);
    }

    @Test
    void anItemWithNoRecipeOpensTheEmptyStateMenu() {
        view.openEmpty(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(SimpleGui.class);
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
