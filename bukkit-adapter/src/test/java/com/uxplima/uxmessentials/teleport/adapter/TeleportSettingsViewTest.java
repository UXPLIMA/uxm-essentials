package com.uxplima.uxmessentials.teleport.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.TeleportSettingsView;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the teleport per-player settings panel. The panel draws one toggle per conf slot,
 * each reading and writing the same {@link TeleportFlags} the {@code /tptoggle} and {@code /tpauto} commands
 * do: opening reflects the stored flag, a click flips it through the port, and an in-place re-render shows the
 * new value. The panel now rides the menu engine's property-editor runtime, so the window is a {@link MenuHolder}
 * routed by the one {@link MenuListener}. The scheduler is synchronous so the off-thread setter runs inline.
 */
class TeleportSettingsViewTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        flags = new FakeFlags();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersBothTogglesAtTheirConfSlots(@TempDir Path dir) throws Exception {
        view(dir).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.ENDER_PEARL); // accept-requests toggle
        assertThat(inv.getItem(15).getType()).isEqualTo(Material.LIME_DYE); // auto-accept toggle
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back / close
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
    }

    @Test
    void clickingAcceptToggleFlipsTheStoredFlag(@TempDir Path dir) throws Exception {
        flags.accepts = true; // default: accepting requests
        view(dir).open(player, viewer);

        fireClick(11, ClickType.LEFT);

        assertThat(flags.accepts).isFalse(); // flipped off through the port
    }

    @Test
    void clickingAutoAcceptToggleFlipsTheStoredFlag(@TempDir Path dir) throws Exception {
        assertThat(flags.auto).isFalse(); // default: not auto-accepting
        view(dir).open(player, viewer);

        fireClick(15, ClickType.LEFT);

        assertThat(flags.auto).isTrue(); // flipped on through the port
    }

    @Test
    void openingReflectsTheStoredStateAfterAFlip(@TempDir Path dir) throws Exception {
        flags.accepts = true; // open shows ON
        TeleportSettingsView view = view(dir);
        view.open(player, viewer);
        assertThat(loreOf(11)).contains("teleport.gui.settings.value-on");

        fireClick(11, ClickType.LEFT); // accept -> off; the panel re-renders to the new state

        // The freshly rendered toggle's value lore resolves the OFF key, proving the re-render read the flip.
        assertThat(loreOf(11)).contains("teleport.gui.settings.value-off");
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

    private TeleportSettingsView view(Path dir) throws Exception {
        writeLayout(dir);
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new TeleportSettingsView(guiText, scheduler, layouts, new KeyMessages(), flags, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel can open through the runtime. */
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
        Path file = dir.resolve("modules").resolve("teleport").resolve("gui").resolve("teleport-settings.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [11, 15]
                back-slot = 22
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** An in-memory {@link TeleportFlags} so a flip is observable without a live PDC. */
    private static final class FakeFlags implements TeleportFlags {
        private boolean accepts = true;
        private boolean auto = false;
        private final Set<String> blocks = new HashSet<>();

        @Override
        public boolean acceptsRequests(PlayerRef who) {
            return accepts;
        }

        @Override
        public boolean toggleRequests(PlayerRef who) {
            accepts = !accepts;
            return accepts;
        }

        @Override
        public void setAcceptsRequests(PlayerRef who, boolean accepting) {
            accepts = accepting;
        }

        @Override
        public boolean autoAccepts(PlayerRef who) {
            return auto;
        }

        @Override
        public boolean toggleAutoAccepts(PlayerRef who) {
            auto = !auto;
            return auto;
        }

        @Override
        public boolean hasBlocked(PlayerRef target, PlayerRef requester) {
            return blocks.contains(key(target, requester));
        }

        @Override
        public void block(PlayerRef blocker, PlayerRef requester) {
            blocks.add(key(blocker, requester));
        }

        @Override
        public void unblock(PlayerRef blocker, PlayerRef requester) {
            blocks.remove(key(blocker, requester));
        }

        private static String key(PlayerRef a, PlayerRef b) {
            UUID au = a.uuid();
            UUID bu = b.uuid();
            return au + ":" + bu;
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
