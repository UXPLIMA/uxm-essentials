package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpCategoriesMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The player-warps landing ({@code pwarp-categories}) a bare {@code /pwarp} opens. Proves the shipped spec loads and
 * renders its quick-entry buttons and the defined category buttons over a real engine, that each quick entry opens the
 * browse with the right preset filter ({@code owner} / {@code favouritesOf} / {@code sort}, or none), that a category
 * button opens the browse filtered to that category, and that an empty category set simply draws no category buttons.
 * Drives the façade through a synchronous scheduler so the off-thread snapshot and the entity render run inline; the
 * browse it hands off to is a spy that records the (viewer, filters) each entry opens it with.
 */
class PwarpCategoriesMenuTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final int BROWSE_ALL_SLOT = 10;
    private static final int MY_WARPS_SLOT = 12;
    private static final int FAVOURITES_SLOT = 14;
    private static final int TOP_RATED_SLOT = 16;
    private static final int FIRST_CATEGORY_SLOT = 27;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private SyncScheduler scheduler;
    private List<WarpCategory> categories;

    @Nullable private PlayerRef openedViewer;

    @Nullable private Map<String, String> openedFilters;

    private PlayerWarpCategoriesMenu menu;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        categories = List.of(
                new WarpCategory("adventure", "Adventure", Optional.of("MAP"), List.of(), 0, Optional.empty()),
                new WarpCategory("shops", "Shops", Optional.empty(), List.of(), 1, Optional.empty()));
        openedViewer = null;
        openedFilters = null;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theSpecLoadsAndRendersTheQuickEntriesAndCategoryButtons() {
        Inventory inv = open();

        assertThat(inv.getItem(BROWSE_ALL_SLOT).getType()).isEqualTo(Material.BOOKSHELF);
        assertThat(inv.getItem(MY_WARPS_SLOT).getType()).isEqualTo(Material.WRITABLE_BOOK);
        assertThat(inv.getItem(FAVOURITES_SLOT).getType()).isEqualTo(Material.NETHER_STAR);
        assertThat(inv.getItem(TOP_RATED_SLOT).getType()).isEqualTo(Material.DIAMOND);
        // The categories flow into the grid ordered by their configured slot: adventure (its MAP icon) then shops
        // (which has no configured material, so the fallback CHEST).
        assertThat(inv.getItem(FIRST_CATEGORY_SLOT).getType()).isEqualTo(Material.MAP);
        assertThat(inv.getItem(FIRST_CATEGORY_SLOT + 1).getType()).isEqualTo(Material.CHEST);
    }

    @Test
    void browseAllOpensTheBrowseWithNoPresetFilter() {
        open();

        fireClick(BROWSE_ALL_SLOT, ClickType.LEFT);

        assertThat(openedViewer).isEqualTo(viewer);
        assertThat(openedFilters).isEmpty();
    }

    @Test
    void myWarpsOpensTheBrowseWithTheOwnerPreset() {
        open();

        fireClick(MY_WARPS_SLOT, ClickType.LEFT);

        assertThat(openedFilters)
                .containsExactly(Map.entry("owner", viewer.uuid().toString()));
    }

    @Test
    void favouritesOpensTheBrowseWithTheFavouritesPreset() {
        open();

        fireClick(FAVOURITES_SLOT, ClickType.LEFT);

        assertThat(openedFilters)
                .containsExactly(Map.entry("favouritesOf", viewer.uuid().toString()));
    }

    @Test
    void topRatedOpensTheBrowseOnTheRatingSort() {
        open();

        fireClick(TOP_RATED_SLOT, ClickType.LEFT);

        assertThat(openedFilters).containsExactly(Map.entry("sort", "rating"));
    }

    @Test
    void clickingACategoryOpensTheBrowseFilteredToIt() {
        open();

        fireClick(FIRST_CATEGORY_SLOT, ClickType.LEFT);

        assertThat(openedFilters).containsExactly(Map.entry("category", "adventure"));
    }

    @Test
    void anEmptyCategorySetDrawsNoCategoryButtons() {
        categories = List.of();
        Inventory inv = open();

        // With no categories the grid draws nothing in its first content slot — empty (or the backdrop), never a
        // category button. The quick-entry buttons above still render.
        org.bukkit.inventory.ItemStack cell = inv.getItem(FIRST_CATEGORY_SLOT);
        assertThat(cell == null || cell.getType().isAir() || cell.getType() == FILLER)
                .isTrue();
        assertThat(inv.getItem(BROWSE_ALL_SLOT).getType()).isEqualTo(Material.BOOKSHELF);
    }

    private Inventory open() {
        wireEngine();
        menu.open(player, viewer);
        return top();
    }

    private void wireEngine() {
        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        menu = new PlayerWarpCategoriesMenu(
                menus,
                scheduler,
                new StubCategories(),
                query -> new com.uxplima.uxmessentials.playerwarps.domain.Page<>(java.util.List.of(), 0L, 0, 50),
                this::recordBrowse,
                (viewer, name) -> {},
                com.uxplima.uxmessentials.playerwarps.application.SponsorConfig.disabled());
        menu.register(bindings, dataFolder, NOOP);
    }

    private void recordBrowse(Player player, PlayerRef viewer, Map<String, String> filters) {
        this.openedViewer = viewer;
        this.openedFilters = filters;
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Serves the test's current category list; the landing snapshots it off the (here inline) scheduler at open. */
    private final class StubCategories implements WarpCategoryRepository {
        @Override
        public Optional<WarpCategory> find(String id) {
            return categories.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<WarpCategory> all() {
            return categories;
        }

        @Override
        public void save(WarpCategory category) {}

        @Override
        public void delete(String id) {}
    }

    /** Every key renders as its key path; the landing asserts on materials and click filters, not on resolved labels. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
