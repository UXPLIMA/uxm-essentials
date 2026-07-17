package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagState;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link RegionFlagEditorView} over a real menu engine and a fake {@link RegionService}
 * (WorldGuard is not on the test classpath — the editor is exercised through the port): it draws one icon per
 * configured flag, reflects each flag's current value (a denied flag shows the deny icon), and a click cycles the
 * flag to its next state and writes that value back through {@link RegionService#setFlag} — after which the reopened
 * panel reflects the new value.
 */
class RegionFlagEditorViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final RegionRef REGION = new RegionRef(WORLD, "spawn");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private FakeRegionService service;
    private RegionFlagEditorView editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
        service = new FakeRegionService();

        Scheduler scheduler = new SyncScheduler();
        Messages messages = keyEcho();
        GuiText guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        Menus menus = engine.menus();
        editor = new RegionFlagEditorView(
                menus,
                guiText,
                scheduler,
                messages,
                service,
                List.of("pvp", "build", "mob-spawning"),
                EntityListLayout.paginatedDefault(Material.GRAY_DYE));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void drawsAnIconPerConfiguredFlag() {
        editor.open(staffRef, REGION);

        Inventory inv = staff.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(0)).isNotNull();
        assertThat(inv.getItem(1)).isNotNull();
        assertThat(inv.getItem(2)).isNotNull();
    }

    @Test
    void reflectsAFlagsValueAndCyclesItThroughThePortOnClick() {
        // pvp is denied in the region, so its icon must render as the deny icon (reflecting the live value).
        service.setInitial("pvp", FlagState.DENY);

        editor.open(staffRef, REGION);
        Inventory inv = staff.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.RED_DYE);

        // Clicking pvp cycles DENY -> UNSET and writes the empty (cleared) value back through the port.
        fireClick(0);
        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("pvp", ""));

        // The reopened panel reflects the new value: pvp is now unset (the unset icon).
        Inventory reopened = staff.getOpenInventory().getTopInventory();
        assertThat(reopened.getItem(0).getType()).isEqualTo(Material.GRAY_DYE);
    }

    private void fireClick(int slot) {
        InventoryView view = staff.getOpenInventory();
        server.getPluginManager()
                .callEvent(new org.bukkit.event.inventory.InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
    }

    /** An in-memory {@link RegionService} that serves a region's state flags and records the last {@code setFlag}. */
    private static final class FakeRegionService implements RegionService {
        private final Map<String, FlagValue> flags = new LinkedHashMap<>();
        private @Nullable FlagValue lastSetFlag;

        void setInitial(String name, FlagState state) {
            flags.put(name, FlagValue.of(name, state));
        }

        @Nullable FlagValue lastSetFlag() {
            return lastSetFlag;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return List.of(REGION);
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return Optional.of(REGION);
        }

        @Override
        public List<FlagValue> flags(RegionRef region) {
            return new ArrayList<>(flags.values());
        }

        @Override
        public List<String> members(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> owners(RegionRef region) {
            return List.of();
        }

        @Override
        public int priority(RegionRef region) {
            return 0;
        }

        @Override
        public RegionRef create(WorldRef world, String id, Position min, Position max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFlag(RegionRef region, FlagValue flag) {
            this.lastSetFlag = flag;
            if (flag.state() == FlagState.UNSET) {
                flags.remove(flag.name());
            } else {
                flags.put(flag.name(), flag);
            }
        }

        @Override
        public void applyMemberChange(RegionMemberChange change) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            throw new UnsupportedOperationException();
        }
    }

    /** Runs every scheduler hop inline. */
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
