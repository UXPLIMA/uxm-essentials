package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpIconMenu;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-icon picker ({@code pwarp-icon}) the manage panel's icon button opens. Proves the shipped spec loads and
 * renders its palette over a real engine, that a click on a palette cell sets that material as the warp's icon through
 * {@link EditPlayerWarp#setIcon} and returns to the manage panel, that the reset button clears the icon, and that the
 * back button reopens the manage panel without touching the icon. Drives the façade through a synchronous scheduler so
 * the write and the reopen run inline.
 */
class PwarpIconMenuTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final int RESET_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final PlayerWarpName NAME = PlayerWarpName.of("base");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private SyncScheduler scheduler;
    private EditPlayerWarp editPlayerWarp;
    private final List<PlayerWarpName> manageOpened = new ArrayList<>();
    private PlayerWarpIconMenu menu;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        editPlayerWarp = mock(EditPlayerWarp.class);
        manageOpened.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theSpecLoadsAndRendersThePaletteWithItsControls() {
        Inventory inv = open();

        // The first content cell is a palette material (not the backdrop), and the bottom row carries the reset and
        // the back button.
        assertThat(inv.getItem(0).getType()).isNotEqualTo(FILLER);
        assertThat(inv.getItem(0).getType().isAir()).isFalse();
        assertThat(inv.getItem(RESET_SLOT).getType()).isEqualTo(Material.BARRIER);
        assertThat(inv.getItem(BACK_SLOT).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clickingAPaletteMaterialSetsTheWarpIconAndReopensManage() {
        Inventory inv = open();
        Material picked = inv.getItem(0).getType();

        fireClick(0, ClickType.LEFT);

        verify(editPlayerWarp).setIcon(viewer, NAME, Optional.of(IconSpec.of(picked.name())));
        assertThat(manageOpened).containsExactly(NAME);
    }

    @Test
    void clickingResetClearsTheCustomIconAndReopensManage() {
        open();

        fireClick(RESET_SLOT, ClickType.LEFT);

        verify(editPlayerWarp).setIcon(viewer, NAME, Optional.empty());
        assertThat(manageOpened).containsExactly(NAME);
    }

    @Test
    void clickingBackReopensManageWithoutChangingTheIcon() {
        open();

        fireClick(BACK_SLOT, ClickType.LEFT);

        verify(editPlayerWarp, never()).setIcon(any(), any(), any());
        assertThat(manageOpened).containsExactly(NAME);
    }

    private Inventory open() {
        wireEngine();
        menu.open(viewer, NAME);
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
        menu = new PlayerWarpIconMenu(menus, scheduler, editPlayerWarp, (ref, name) -> manageOpened.add(name));
        menu.register(bindings, dataFolder, NOOP);
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

    /** Every key renders as its key path; the picker asserts on materials and behaviour, not on resolved labels. */
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
