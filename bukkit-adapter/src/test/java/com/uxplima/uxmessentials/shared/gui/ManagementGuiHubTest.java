package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.shared.menu.TileText;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the management-GUI hub (SP0-b): the {@link ManagementGuiRegistry} holds and
 * returns the registered entries, filters them by the viewer's permission, and the {@link ManagementHubView}
 * draws only the permitted entries into the conf'd slots and routes a click on one to that entry's opener.
 * The hub geometry comes entirely from a temp layout conf (no hardcoded slots); the scheduler is a
 * synchronous double so the entity-bound build runs inline, and uxmLib's menu listener is installed against
 * a mock plugin (reset on teardown).
 */
class ManagementGuiHubTest {

    private static final String NODE_A = "uxmessentials.alpha.gui";
    private static final String NODE_B = "uxmessentials.beta.gui";

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registryHoldsAndReturnsEntriesInRegistrationOrder() {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        ManagementGuiEntry alpha = entry("alpha", Material.DIAMOND, NODE_A);
        ManagementGuiEntry beta = entry("beta", Material.EMERALD, NODE_B);
        registry.register(alpha);
        registry.register(beta);

        assertThat(registry.entries()).containsExactly(alpha, beta);
    }

    @Test
    void registryFiltersEntriesByViewerPermission() {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        registry.register(entry("alpha", Material.DIAMOND, NODE_A));
        registry.register(entry("beta", Material.EMERALD, NODE_B));
        FakePermissions permissions = new FakePermissions(Set.of(NODE_A));

        List<ManagementGuiEntry> permitted = registry.entriesFor(viewer, permissions);

        assertThat(permitted).hasSize(1);
        assertThat(permitted.get(0).id()).isEqualTo("alpha");
    }

    @Test
    void hubRendersOnlyThePermittedEntries(@TempDir Path dir) throws Exception {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        registry.register(entry("alpha", Material.DIAMOND, NODE_A));
        registry.register(entry("beta", Material.EMERALD, NODE_B));
        // The viewer holds only beta's node, so only beta's icon is drawn.
        hub(dir, registry, new FakePermissions(Set.of(NODE_B))).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.EMERALD);
        // The second content slot has no permitted entry, so the engine draws the layout filler there.
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void clickingAnEntryInvokesItsOpener(@TempDir Path dir) throws Exception {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        AtomicReference<String> opened = new AtomicReference<>();
        registry.register(
                new ManagementGuiEntry("alpha", Key.A, Material.DIAMOND, NODE_A, (p, v) -> opened.set("alpha")));
        hub(dir, registry, new FakePermissions(Set.of(NODE_A))).open(player, viewer);

        fireClick(10);

        assertThat(opened.get()).isEqualTo("alpha");
    }

    @Test
    void anEntryIsTitledWithItsOwnPanelNameRatherThanItsModuleId(@TempDir Path dir) throws Exception {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        registry.register(new ManagementGuiEntry("alpha", Key.PANEL, Material.DIAMOND, NODE_A, (p, v) -> {}));
        hub(dir, registry, new FakePermissions(Set.of(NODE_A))).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        // The id is a wiring key and reads like one; the title is the name of the panel behind the icon.
        assertThat(TileText.title(inv.getItem(10))).isEqualTo(Key.PANEL.key());
    }

    @Test
    void emptyRegistryRendersNoEntries(@TempDir Path dir) throws Exception {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        hub(dir, registry, new FakePermissions(Set.of())).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        // No entries, so every content slot shows the layout filler rather than an icon.
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ManagementHubView hub(Path dir, ManagementGuiRegistry registry, Permissions permissions) throws Exception {
        return new ManagementHubView(menus, guiText, scheduler, permissions, registry, layout(dir));
    }

    private EntityListLayout layout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("management").resolve("gui").resolve("hub.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 6
                nav-icon = "ARROW"
                filler = "GRAY_STAINED_GLASS_PANE"
                content-slots = [10, 11, 12]
                prev-slot = 48
                next-slot = 50
                """);
        return new GuiLayouts(dir, NOOP)
                .loadEntityList("management", "hub", EntityListLayout.paginatedDefault(Material.NETHER_STAR));
    }

    private ManagementGuiEntry entry(String id, Material icon, String permission) {
        return new ManagementGuiEntry(id, Key.A, icon, permission, (p, v) -> {});
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A label key the entries reuse; its text is irrelevant to slot/material/opener assertions. */
    private enum Key implements MessageKey {
        A(GuiMessageKey.HUB_ENTRY_LORE.key()),
        PANEL(GuiMessageKey.HUB_TITLE.key());

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Grants exactly the nodes it is constructed with; quotas are unused by the hub. */
    private static final class FakePermissions implements Permissions {
        private final Set<String> granted;

        FakePermissions(Set<String> granted) {
            this.granted = new HashSet<>(granted);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
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
