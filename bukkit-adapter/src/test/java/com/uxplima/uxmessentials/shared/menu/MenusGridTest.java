package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridHandlers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the engine's {@link Menus#openGrid} slot-grid primitive: a grid opens as a
 * {@link MenuHolder}-backed window the one {@link MenuListener} recognises, paints an item preview at a filled menu
 * slot and the caller's placeholder at an empty one, reserves the last row for the control bar, and routes a content /
 * control / nav click to the caller's handlers. A six-row menu paginates so the control row never collides with
 * content, and the un-paged {@code menuSlot = canvasSlot + page*pageSize} math is what the handler is handed.
 */
class MenusGridTest {

    private static final Material EMPTY = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private static final Material BLOCKER = Material.GRAY_STAINED_GLASS_PANE;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, new PlaceholderRegistry()), new ConditionRegistry());
        menus = new Menus(renderer, scheduler, new ListSourceRegistry());
        MenuListener listener =
                new MenuListener(renderer, new ActionRegistry(), new ConditionRegistry(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void gridPaintsFilledSlotsPlaceholdersAndAControlBar() {
        GridHandlers noop = new GridHandlers((view, player, menuSlot, filled, kind) -> {});
        menus.openGrid(viewer, spec(1, () -> Map.of(0, item("DIAMOND")), p -> {}), noop);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(18); // one content row + one control row
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.DIAMOND); // the filled menu slot's preview
        assertThat(inv.getItem(1).getType()).isEqualTo(EMPTY); // an empty menu slot's placeholder
        assertThat(inv.getItem(13).getType()).isEqualTo(Material.EMERALD); // the control button at column 4 of row 2
    }

    @Test
    void clicksRouteToTheContentAndControlHandlers() {
        AtomicReference<int[]> lastSlot = new AtomicReference<>();
        AtomicBoolean controlRan = new AtomicBoolean();
        GridHandlers handlers = new GridHandlers(
                (view, player, menuSlot, filled, kind) -> lastSlot.set(new int[] {menuSlot, filled ? 1 : 0}));
        menus.openGrid(viewer, spec(1, () -> Map.of(0, item("DIAMOND")), p -> controlRan.set(true)), handlers);

        fireClick(0, ClickType.LEFT);
        assertThat(lastSlot.get()).containsExactly(0, 1); // filled slot 0

        fireClick(1, ClickType.LEFT);
        assertThat(lastSlot.get()).containsExactly(1, 0); // empty slot 1

        fireClick(13, ClickType.LEFT);
        assertThat(controlRan).isTrue(); // the control button ran
    }

    @Test
    void aSixRowMenuPaginatesWithTheControlRowClearOfContent() {
        AtomicReference<int[]> lastSlot = new AtomicReference<>();
        GridHandlers handlers = new GridHandlers(
                (view, player, menuSlot, filled, kind) -> lastSlot.set(new int[] {menuSlot, filled ? 1 : 0}));
        // A menu slot on the second page (50) only reachable after a page flip; the control row is slots 45..53.
        menus.openGrid(viewer, spec(6, () -> Map.of(50, item("DIAMOND")), p -> {}), handlers);

        Inventory page0 = player.getOpenInventory().getTopInventory();
        assertThat(page0.getSize()).isEqualTo(54);
        assertThat(page0.getItem(53).getType()).isEqualTo(Material.ARROW); // next button at column 8 of the control row

        fireClick(53, ClickType.LEFT); // flip to page 1

        Inventory page1 = player.getOpenInventory().getTopInventory();
        // menuSlot 50 = canvasSlot 5 + page 1 * pageSize 45, so the item lands at canvas slot 5.
        assertThat(page1.getItem(5).getType()).isEqualTo(Material.DIAMOND);
        assertThat(page1.getItem(9).getType()).isEqualTo(BLOCKER); // menu slot 54 is off the menu: an inert blocker
        assertThat(page1.getItem(45).getType()).isEqualTo(Material.ARROW); // previous button at column 0

        fireClick(5, ClickType.LEFT);
        assertThat(lastSlot.get()).containsExactly(50, 1); // the un-paged menu slot reaches the handler
    }

    // --- helpers ---

    private GridSpec spec(
            int rows,
            Supplier<Map<Integer, MenuItemSpec>> content,
            java.util.function.Consumer<org.bukkit.entity.Player> onControl) {
        return new GridSpec(
                Component.text("grid"),
                rows,
                content,
                icon(EMPTY),
                icon(BLOCKER),
                icon(Material.ARROW),
                icon(Material.ARROW),
                List.of(new GridSpec.Control(4, icon(Material.EMERALD), onControl)));
    }

    private static MenuItemSpec item(String material) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                material,
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref>of(),
                new ClickSpec(
                        Map.<ClickKind, List<com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref>>of(),
                        Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static ItemStack icon(Material material) {
        return ItemBuilder.of(material).name(Component.empty()).build();
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
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
