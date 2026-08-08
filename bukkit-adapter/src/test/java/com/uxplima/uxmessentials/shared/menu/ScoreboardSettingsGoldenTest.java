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

import com.uxplima.uxmessentials.scoreboard.adapter.inbound.gui.ScoreboardSettingsView;
import com.uxplima.uxmessentials.scoreboard.application.ScoreboardMessageKey;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
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
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
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
 * The scoreboard settings golden test. The migrated {@link ScoreboardSettingsView} now opens through
 * {@link Menus#openEditor} (its {@link SettingsPanelView} is a thin shim over the engine), so this asserts the
 * engine-rendered editor draws the exact single-toggle panel the bespoke view drew: same material and plain name at
 * the toggle and back slots, and the same value-lore for both shown/hidden states. The baseline is frozen from the
 * panel's geometry + catalog keys (the shim replaces the live "before"), the way the kit/warp golden tests freeze a
 * baseline. A real click on the toggle through the engine's own {@link MenuListener} then proves the migrated path
 * flips the stored bit through the same {@link ToggleScoreboard} use case the {@code /scoreboard} command drives.
 */
class ScoreboardSettingsGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final int TOGGLE_SLOT = 13;
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
    private InMemoryVisibility visibility;
    private ToggleScoreboard toggle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        visibility = new InMemoryVisibility();
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        toggle = new ToggleScoreboard(visibility, notifier, new NoopEvents());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenShown() throws Exception {
        assertParity(true);
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenHidden() throws Exception {
        visibility.toggle(viewer); // start hidden
        assertParity(false);
    }

    private void assertParity(boolean shown) throws Exception {
        Map<Integer, Snapshot> baseline = baseline(shown);
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
        assertThat(engine).containsKey(TOGGLE_SLOT);
        assertThat(engine).containsKey(BACK_SLOT);
    }

    @Test
    void clickingTheToggleThroughTheEngineHidesTheBoardAndReRendersTheSlot() throws Exception {
        view().open(player, viewer);

        Inventory before = player.getOpenInventory().getTopInventory();
        assertThat(visibility.hidden(viewer)).isFalse();
        assertThat(valueLoreOf(before.getItem(TOGGLE_SLOT))).isEqualTo("value=" + shownValue(true));

        fireClick(TOGGLE_SLOT, ClickType.LEFT);

        // Flipped through the same ToggleScoreboard use case the /scoreboard command drives.
        assertThat(visibility.hidden(viewer)).isTrue();
        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after).isSameAs(before); // in-place re-render: no second openInventory, same holder
        assertThat(valueLoreOf(after.getItem(TOGGLE_SLOT))).isEqualTo("value=" + shownValue(false));
    }

    // --- snapshots ---

    private Map<Integer, Snapshot> snapshotEngine() throws Exception {
        view().open(player, viewer);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private Map<Integer, Snapshot> baseline(boolean shown) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        out.put(
                TOGGLE_SLOT,
                new Snapshot(
                        Material.PAINTING, ScoreboardMessageKey.GUI_VISIBILITY.key(), "value=" + shownValue(shown)));
        out.put(BACK_SLOT, new Snapshot(Material.ARROW, ScoreboardMessageKey.GUI_BACK.key(), ""));
        return out;
    }

    private String shownValue(boolean shown) {
        return shown ? ScoreboardMessageKey.GUI_VALUE_SHOWN.key() : ScoreboardMessageKey.GUI_VALUE_HIDDEN.key();
    }

    // --- harness ---

    private ScoreboardSettingsView view() throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new ScoreboardSettingsView(guiText, scheduler, layouts, messages, visibility, toggle, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel opens through the runtime. */
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

    private void writeLayout() throws Exception {
        Path file = dir.resolve("modules").resolve("scoreboard").resolve("gui").resolve("scoreboard-settings.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [13]
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

    private static final class InMemoryVisibility implements ScoreboardVisibilityStore {
        private final Set<UUID> hidden = new HashSet<>();

        @Override
        public boolean hidden(PlayerRef who) {
            return hidden.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            if (hidden.add(who.uuid())) {
                return true;
            }
            hidden.remove(who.uuid());
            return false;
        }

        @Override
        public void forget(PlayerRef who) {
            hidden.remove(who.uuid());
        }
    }

    /** Special-cases the value-lore key to wrap the substituted value; every other key echoes itself. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(ScoreboardMessageKey.GUI_VALUE_LORE.key())) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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
