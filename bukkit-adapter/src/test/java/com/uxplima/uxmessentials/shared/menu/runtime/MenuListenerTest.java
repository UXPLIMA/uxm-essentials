package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
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
 * MockBukkit coverage of the single menu listener. The listener is registered against the plugin manager and a real
 * {@link InventoryClickEvent} / {@link InventoryCloseEvent} is fired against the open window, so the whole
 * holder-recognition, cancel, dispatch, and teardown chain runs end to end. The scheduler is synchronous so the
 * entity-bound action hop runs inline. The renderer is wired with real no-op binding deps; these cases never
 * navigate, so it is only there to satisfy the constructor.
 */
class MenuListenerTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock viewer;
    private ActionRegistry actions;
    private MenuListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = server.addPlayer("Viewer");
        actions = new ActionRegistry();
        ConditionRegistry conditions = new ConditionRegistry();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new GuiText(new KeyMessages()), new PlaceholderRegistry()),
                conditions,
                new ListSourceRegistry());
        listener = new MenuListener(renderer, actions, conditions, new SyncScheduler(), plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void leftClickRunsBoundActionAndCancelsEvent() {
        AtomicInteger hits = new AtomicInteger();
        actions.register("test:hit", ctx -> hits.incrementAndGet());
        MenuHolder holder = holderWithSlotZero(new RenderedSlot(itemBoundLeftTo("test:hit"), null));
        openMenu(holder);

        InventoryClickEvent event = clickEvent(0, ClickType.LEFT);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void closeCancelsTheRefreshHandle() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        MenuHolder holder = holderWithSlotZero(new RenderedSlot(itemBoundLeftTo("test:hit"), null));
        holder.setRefreshHandle(() -> cancelled.set(true));
        openMenu(holder);

        server.getPluginManager().callEvent(new InventoryCloseEvent(viewer.getOpenInventory()));

        assertThat(cancelled.get()).isTrue();
    }

    @Test
    void clickInNonMenuInventoryIsIgnored() {
        AtomicInteger hits = new AtomicInteger();
        actions.register("test:hit", ctx -> hits.incrementAndGet());
        Inventory plain = server.createInventory(null, 27);
        viewer.openInventory(plain);

        InventoryClickEvent event = clickEvent(0, ClickType.LEFT);
        server.getPluginManager().callEvent(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(hits.get()).isZero();
    }

    private void openMenu(MenuHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 9, net.kyori.adventure.text.Component.text("menu"));
        holder.attach(inv);
        viewer.openInventory(inv);
    }

    private MenuHolder holderWithSlotZero(RenderedSlot rs) {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems {}");
        MenuContext ctx = MenuContext.of(new PlayerRef(viewer.getUniqueId(), viewer.getName()), null, 0);
        MenuHolder holder = new MenuHolder("t", spec, ctx);
        holder.recordSlot(0, rs);
        return holder;
    }

    private InventoryClickEvent clickEvent(int slot, ClickType type) {
        InventoryView view = viewer.getOpenInventory();
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
    }

    private static MenuItemSpec itemBoundLeftTo(String actionId) {
        return new MenuItemSpec(
                SlotSet.parse(List.of("0"), 9),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(ClickKind.LEFT, List.of(Ref.parse(actionId))), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
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
