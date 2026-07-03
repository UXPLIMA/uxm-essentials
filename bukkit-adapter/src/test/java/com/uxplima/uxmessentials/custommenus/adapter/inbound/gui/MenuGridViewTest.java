package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /menu editor} slot-grid canvas: the grid opens as a {@link MenuHolder} window
 * showing each item at its slot, a subtle placeholder at every empty slot, and a bottom control bar; a left-click on
 * an empty slot adds a default item, a right-then-left click moves an item, and a shift-click clears one behind the
 * engine confirm. Save writes the edited working copy through the P0 path and hot-reloads it. Everything is an engine
 * window, so the grid never touches a raw Bukkit inventory.
 */
class MenuGridViewTest {

    private static final int BACK_SLOT = 12; // control row (9) + BACK_COLUMN 3
    private static final int SAVE_SLOT = 14; // control row (9) + SAVE_COLUMN 5
    private static final int CONFIRM_YES_SLOT = 11; // ConfirmRenderer.YES_SLOT

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private CustomMenuLoader loader;
    private final List<String> names = new ArrayList<>();
    private MenuGridView view;

    @TempDir
    Path menusDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, NOOP);
        writeMenu("alpha", 1, "x { slot = 0, material = STONE, click { left = [\"close\"] } }");
        writeMenu("big", 6, "x { slot = 50, material = STONE, click { left = [\"close\"] } }");
        names.addAll(loader.loadFrom(menusDir).loadedNames());

        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, NOOP);
        MenuEditorService service = new MenuEditorService(
                menusDir,
                persistence,
                menus::registeredSpec,
                name -> Optional.empty(),
                this::reloadOne,
                this::forget,
                NOOP);
        view = new MenuGridView(menus, guiText, scheduler, service, menus::registeredSpec, (p, id) -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void gridShowsItemsPlaceholdersAndTheControlBar() {
        view.open(player, viewer, "alpha");

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(18); // one content row + one control row
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.STONE); // the item painted at its slot
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE); // empty placeholder
        assertThat(inv.getItem(BACK_SLOT).getType()).isEqualTo(Material.BARRIER); // control bar: back
        assertThat(inv.getItem(SAVE_SLOT).getType()).isEqualTo(Material.EMERALD); // control bar: save
    }

    @Test
    void leftClickingAnEmptySlotAddsADefaultItem() {
        view.open(player, viewer, "alpha");

        fireClick(1, ClickType.LEFT);

        MenuEditSession session = editSession();
        assertThat(itemAt(session, 1)).isPresent(); // a default item now occupies slot 1
        assertThat(session.items()).hasSize(2); // the seeded item plus the new one
    }

    @Test
    void rightThenLeftClickMovesTheItemToTheTargetSlot() {
        view.open(player, viewer, "alpha");

        fireClick(0, ClickType.RIGHT); // pick up the item at slot 0
        fireClick(2, ClickType.LEFT); // drop it on slot 2

        MenuEditSession session = editSession();
        assertThat(itemAt(session, 2)).contains("x"); // the item moved to slot 2
        assertThat(itemAt(session, 0)).isEmpty(); // and left slot 0
    }

    @Test
    void shiftClickClearsTheSlotBehindAConfirm() {
        view.open(player, viewer, "alpha");

        fireClick(0, ClickType.SHIFT_LEFT); // opens the confirm, does not clear yet
        assertThat(itemAt(editSession(), 0)).contains("x");

        fireClick(CONFIRM_YES_SLOT, ClickType.LEFT); // confirm the clear
        assertThat(editSession().items()).isEmpty(); // the item is gone from the working copy
    }

    @Test
    void savingWritesTheEditedSessionAndReloadsIt() {
        view.open(player, viewer, "alpha");
        fireClick(3, ClickType.LEFT); // add a default item at slot 3

        fireClick(SAVE_SLOT, ClickType.LEFT);

        MenuItemSpec added = menus.registeredSpec("alpha").orElseThrow().items().values().stream()
                .filter(item -> item.slots().slots().contains(3))
                .findFirst()
                .orElse(null);
        assertThat(added).isNotNull(); // the reloaded menu carries the item added in the grid
        assertThat(menus.registeredSpec("alpha").orElseThrow().items()).hasSize(2);
    }

    @Test
    void aSixRowMenuPaginatesAcrossTwoPages() {
        view.open(player, viewer, "big");

        Inventory page0 = player.getOpenInventory().getTopInventory();
        assertThat(page0.getSize()).isEqualTo(54);
        assertThat(page0.getItem(53).getType()).isEqualTo(Material.ARROW); // next button on the control row

        fireClick(53, ClickType.LEFT); // flip to page 1

        Inventory page1 = player.getOpenInventory().getTopInventory();
        assertThat(page1.getItem(5).getType()).isEqualTo(Material.STONE); // menu slot 50 lands at canvas slot 5
    }

    // --- helpers ---

    private MenuEditSession editSession() {
        return view.editSession(viewer.uuid()).orElseThrow();
    }

    private static Optional<String> itemAt(MenuEditSession session, int slot) {
        return session.items().entrySet().stream()
                .filter(entry -> entry.getValue().slots().slots().contains(slot))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private CustomMenuLoader.SingleLoad reloadOne(String name) {
        CustomMenuLoader.SingleLoad result = loader.loadSingle(menusDir, name);
        if (result.found() && result.loaded() > 0 && !names.contains(name)) {
            names.add(name);
        }
        return result;
    }

    private void forget(String name) {
        menus.unregisterSpec(name);
        names.remove(name);
    }

    private void writeMenu(String name, int rows, String item) {
        String hocon = "rows = " + rows + "\nitems { " + item + " }\n";
        try {
            Files.writeString(menusDir.resolve(name + ".conf"), hocon);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to seed menu " + name, failure);
        }
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView openView = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                openView, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

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
