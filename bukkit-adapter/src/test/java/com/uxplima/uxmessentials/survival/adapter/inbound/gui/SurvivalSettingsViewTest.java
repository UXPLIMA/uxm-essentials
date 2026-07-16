package com.uxplima.uxmessentials.survival.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /survival} settings panel: it draws one row per toggleable survival mechanic the
 * viewer may switch, hides the mechanics the server disabled or the viewer lacks the {@code .toggle} permission for,
 * and a click flips the mechanic's PDC toggle through the same {@link PdcSurvivalToggles} the {@code /treefeller},
 * {@code /veinminer}, … commands use. Mirrors {@code PosesSettingsViewTest}; the scheduler is synchronous so the
 * redraw runs inline.
 */
class SurvivalSettingsViewTest {

    private static final String TREEFELLER = "uxmessentials.survival.treefeller.toggle";
    private static final String VEINMINER = "uxmessentials.survival.veinminer.toggle";
    private static final String AUTOPICKUP = "uxmessentials.survival.autopickup.toggle";

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private PdcSurvivalToggles toggles;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        toggles = new PdcSurvivalToggles();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openingDrawsAnEnabledPermittedMechanicAtEachSlotInOrder(@TempDir Path dir) throws Exception {
        view(dir, allEnabled(), Set.of(TREEFELLER, AUTOPICKUP)).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.OAK_LOG); // tree-feller (permitted)
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.HOPPER); // auto-pickup (permitted, next slot)
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back / close
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
    }

    @Test
    void aMechanicTheViewerCannotToggleIsHidden(@TempDir Path dir) throws Exception {
        view(dir, allEnabled(), Set.of(TREEFELLER)).open(player, viewer);

        // Tree-feller is permitted and shown; veinminer is not permitted, so its icon appears in no slot.
        assertThat(inventoryContains(Material.OAK_LOG)).isTrue();
        assertThat(inventoryContains(Material.IRON_ORE)).isFalse();
    }

    @Test
    void aGloballyDisabledMechanicIsHiddenEvenWithPermission(@TempDir Path dir) throws Exception {
        // Veinminer disabled in config, but the viewer holds every toggle node: the disabled mechanic still drops out.
        view(dir, Map.of("veinminer.enabled", false), Set.of(TREEFELLER, VEINMINER, AUTOPICKUP))
                .open(player, viewer);

        assertThat(inventoryContains(Material.OAK_LOG)).isTrue();
        assertThat(inventoryContains(Material.IRON_ORE)).isFalse();
    }

    @Test
    void clickingAMechanicFlipsItsStoredPdcToggle(@TempDir Path dir) throws Exception {
        assertThat(toggles.treeFellerActive(player, true)).isTrue(); // ships on
        view(dir, allEnabled(), Set.of(TREEFELLER)).open(player, viewer);

        fireClick(10, ClickType.LEFT);

        assertThat(toggles.treeFellerActive(player, true)).isFalse(); // flipped off through PdcSurvivalToggles
    }

    @Test
    void theMechanicLoreReflectsTheStoredStateAfterAFlip(@TempDir Path dir) throws Exception {
        view(dir, allEnabled(), Set.of(TREEFELLER)).open(player, viewer);
        assertThat(loreOf(10)).contains("survival.gui.value-on"); // on by default

        fireClick(10, ClickType.LEFT); // now off; the panel re-renders to the new state

        assertThat(loreOf(10)).contains("survival.gui.value-off");
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private SurvivalSettingsView view(Path dir, Map<String, Boolean> enables, Set<String> granted) throws Exception {
        writeLayout(dir);
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        SurvivalConfig config = SurvivalConfig.from(new FixedConfig(enables));
        return new SurvivalSettingsView(
                guiText,
                scheduler,
                layouts,
                new KeyMessages(),
                new GrantingPermissions(granted),
                engine(),
                server,
                toggles,
                config);
    }

    private static Map<String, Boolean> allEnabled() {
        return Map.of();
    }

    /** A minimal editor-capable engine + listener so the panel can open and route clicks through the runtime. */
    private Menus engine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus menus = new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private void writeLayout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("survival").resolve("gui").resolve("survival-settings.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [10, 11, 12, 13, 14, 15, 16]
                back-slot = 22
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private boolean inventoryContains(Material material) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            var item = inv.getItem(slot);
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private String loreOf(int slot) {
        var lore = player.getOpenInventory()
                .getTopInventory()
                .getItem(slot)
                .getItemMeta()
                .lore();
        var serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        StringBuilder out = new StringBuilder();
        if (lore != null) {
            lore.forEach(line -> out.append(serializer.serialize(line)).append('\n'));
        }
        return out.toString();
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A ConfigStore that yields the survival defaults, letting a case flip individual {@code enabled} gates off. */
    private record FixedConfig(Map<String, Boolean> booleans) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return booleans.getOrDefault(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** Grants exactly the nodes in {@code granted}; every other check is a miss. */
    private record GrantingPermissions(Set<String> granted) implements Permissions {
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

    /** Echoes the key and any placeholder values so a rendered value-lore reveals the substituted state. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (placeholders.isEmpty()) {
                return key.key();
            }
            return key.key() + " " + String.join(" ", placeholders.values());
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
