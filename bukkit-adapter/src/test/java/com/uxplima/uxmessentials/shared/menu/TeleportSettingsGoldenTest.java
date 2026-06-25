package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.TeleportSettingsView;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The teleport settings golden test. The migrated {@link TeleportSettingsView} now opens through
 * {@link Menus#openEditor} (its {@link SettingsPanelView} is a thin shim over the engine), so this asserts the
 * engine-rendered editor draws the exact panel the bespoke view drew: same material and same plain name at every
 * slot, and the same value-lore per toggle, for both states. The baseline is frozen from the panel's geometry +
 * catalog keys (the shim replaces the live "before"), the way the kit/warp golden tests freeze a baseline. A real
 * click on the accept slot through the engine's own {@link MenuListener} then proves the migrated path flips the
 * flag through the same {@link TeleportFlags} the {@code /tptoggle} command drives and re-renders the slot.
 */
class TeleportSettingsGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final List<Integer> SLOTS = List.of(11, 15);
    private static final int ACCEPT_SLOT = SLOTS.get(0);
    private static final int AUTO_SLOT = SLOTS.get(1);
    private static final int BACK_SLOT = 22;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Messages messages;
    private SyncScheduler scheduler;
    private FakeFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        flags = new FakeFlags();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenAccepting() throws Exception {
        flags.accepts = true;
        assertParity(true, false);
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenNotAccepting() throws Exception {
        flags.accepts = false;
        assertParity(false, false);
    }

    private void assertParity(boolean accepts, boolean auto) throws Exception {
        Map<Integer, Snapshot> baseline = baseline(accepts, auto);
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
        assertThat(engine).containsKey(ACCEPT_SLOT);
        assertThat(engine).containsKey(AUTO_SLOT);
        assertThat(engine).containsKey(BACK_SLOT);
    }

    @Test
    void clickingTheAcceptToggleThroughTheEngineFlipsTheFlagAndReRendersTheSlot() throws Exception {
        flags.accepts = true;
        view().open(player, viewer);

        Inventory before = player.getOpenInventory().getTopInventory();
        assertThat(valueLoreOf(before.getItem(ACCEPT_SLOT))).isEqualTo("value=" + onOff(true));

        fireClick(ACCEPT_SLOT, ClickType.LEFT);

        // Flipped through the same TeleportFlags port the /tptoggle command drives.
        assertThat(flags.accepts).isFalse();
        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after).isSameAs(before); // in-place re-render: no second openInventory, same holder
        assertThat(valueLoreOf(after.getItem(ACCEPT_SLOT))).isEqualTo("value=" + onOff(false));
    }

    // --- snapshots ---

    private Map<Integer, Snapshot> snapshotEngine() throws Exception {
        view().open(player, viewer);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private Map<Integer, Snapshot> baseline(boolean accepts, boolean auto) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        out.put(
                ACCEPT_SLOT,
                new Snapshot(
                        Material.ENDER_PEARL, TeleportMessageKey.GUI_SETTINGS_ACCEPT.key(), "value=" + onOff(accepts)));
        out.put(
                AUTO_SLOT,
                new Snapshot(
                        Material.LIME_DYE, TeleportMessageKey.GUI_SETTINGS_AUTO_ACCEPT.key(), "value=" + onOff(auto)));
        out.put(BACK_SLOT, new Snapshot(Material.ARROW, TeleportMessageKey.GUI_SETTINGS_BACK.key(), ""));
        return out;
    }

    private String onOff(boolean on) {
        return on ? TeleportMessageKey.GUI_SETTINGS_VALUE_ON.key() : TeleportMessageKey.GUI_SETTINGS_VALUE_OFF.key();
    }

    // --- harness ---

    private TeleportSettingsView view() throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new TeleportSettingsView(guiText, scheduler, layouts, messages, flags, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel opens through the runtime. */
    private Menus engine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        MenuListener listener = new MenuListener(
                renderer, new ActionRegistry(), new ConditionRegistry(), scheduler, plugin, editorRenderer);
        server.getPluginManager().registerEvents(listener, plugin);
        return new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
    }

    private void writeLayout() throws Exception {
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

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item), valueLoreOrEmpty(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static String valueLoreOrEmpty(ItemStack item) {
        List<Component> lore = item.lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private static String valueLoreOf(ItemStack item) {
        List<Component> lore = item.lore();
        assertThat(lore).isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private record Snapshot(Material material, String name, String valueLore) {}

    // --- fakes ---

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

    /** Special-cases the value-lore key to wrap the substituted value; every other key echoes itself. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(TeleportMessageKey.GUI_SETTINGS_VALUE_LORE.key())) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NOOP =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
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
