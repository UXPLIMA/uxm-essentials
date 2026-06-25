package com.uxplima.uxmessentials.communication.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /announce} editor: the list renders one icon per store announcement and a click
 * opens its editor; the enabled toggle and the channel toggles cycle and persist through the store; the world and
 * permission text properties compose into the stored condition string; and delete is gated behind a confirm menu.
 * Every write lands through the {@link AnnouncementStore}, so the editor's CRUD is exercised end to end against an
 * in-memory store. The scheduler runs every hop inline so the off-thread writes land synchronously.
 */
class AnnouncementEditorViewTest {

    // Property slots, in the order AnnouncementEditorView builds its properties (matches announcement-editor.conf).
    private static final List<Integer> PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20);
    private static final int MESSAGE_SLOT = PROPERTY_SLOTS.get(0);
    private static final int ENABLED_SLOT = PROPERTY_SLOTS.get(1);
    private static final int CHAT_SLOT = PROPERTY_SLOTS.get(2);
    private static final int TITLE_SLOT = PROPERTY_SLOTS.get(4);
    private static final int WORLD_SLOT = PROPERTY_SLOTS.get(7);
    private static final int PERMISSION_SLOT = PROPERTY_SLOTS.get(8);
    private static final int DELETE_SLOT = 26;
    private static final int CONFIRM_SLOT =
            11; // the engine confirm window's yes button slot (ConfirmRenderer.YES_SLOT)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private InMemoryAnnouncementStore store;
    private InMemoryAnnouncerSettingsStore settingsStore;
    private AnnouncementEditorView view;

    @BeforeEach
    void setUp(@TempDir java.nio.file.Path dir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        Guis.install(plugin);
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        store = new InMemoryAnnouncementStore();
        settingsStore = new InMemoryAnnouncerSettingsStore();
        TextInput textInput =
                TextInputTestKit.create(plugin, guiText, scheduler, java.nio.file.Path.of("nonexistent"), NOOP);
        // The list stays a uxmLib EntityListView for now; the per-announcement editor and the settings screen open
        // through this one editor-capable engine, routed by the one listener registered here.
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        view = new AnnouncementEditorView(
                menus,
                guiText,
                scheduler,
                new KeyMessages(),
                store,
                settingsStore,
                new GuiLayouts(dir, NOOP),
                textInput);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void listRendersOneIconPerStoreAnnouncement() {
        store.save(StoredAnnouncement.fresh("alpha", "one"));
        store.save(StoredAnnouncement.fresh("beta", "two").withEnabled(false));

        view.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.PAPER); // alpha, enabled
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.GRAY_DYE); // beta, disabled
    }

    @Test
    void clickingAnAnnouncementOpensTheEditor() {
        store.save(StoredAnnouncement.fresh("alpha", "one"));
        view.open(player, viewer);

        fireClick(0, ClickType.LEFT);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(MESSAGE_SLOT)).isNotNull();
        assertThat(inv.getItem(MESSAGE_SLOT).getType()).isEqualTo(Material.WRITABLE_BOOK);
    }

    @Test
    void togglingEnabledPersistsThroughTheStore() {
        store.save(StoredAnnouncement.fresh("alpha", "one").withEnabled(false));
        view.editor().open(player, viewer, store.find("alpha").orElseThrow());

        fireClick(ENABLED_SLOT, ClickType.LEFT);

        assertThat(store.find("alpha"))
                .hasValueSatisfying(a -> assertThat(a.enabled()).isTrue());
    }

    @Test
    void togglingAChannelPersistsThroughTheStore() {
        store.save(StoredAnnouncement.fresh("alpha", "one")); // starts on CHAT only
        view.editor().open(player, viewer, store.find("alpha").orElseThrow());

        fireClick(TITLE_SLOT, ClickType.LEFT); // add the TITLE channel

        assertThat(store.find("alpha"))
                .hasValueSatisfying(
                        a -> assertThat(a.channels()).contains(BroadcastChannel.TITLE, BroadcastChannel.CHAT));

        fireClick(CHAT_SLOT, ClickType.LEFT); // remove CHAT, leaving TITLE

        assertThat(store.find("alpha")).hasValueSatisfying(a -> {
            assertThat(a.channels()).contains(BroadcastChannel.TITLE);
            assertThat(a.channels()).doesNotContain(BroadcastChannel.CHAT);
        });
    }

    @Test
    void theWorldTargetComposesIntoTheCondition() {
        store.save(StoredAnnouncement.fresh("alpha", "one"));
        EditableProperty world = view.editor()
                .propertyAt(WORLD_SLOT, store.find("alpha").orElseThrow())
                .orElseThrow();
        assertThat(world).isInstanceOf(TextProperty.class);

        ((TextProperty) world).applyInput(context(), "hub");

        assertThat(store.find("alpha"))
                .hasValueSatisfying(a -> assertThat(a.condition()).isEqualTo("world:hub"));
    }

    @Test
    void theWorldAndPermissionTargetsComposeTogether() {
        store.save(StoredAnnouncement.fresh("alpha", "one"));

        worldProperty().applyInput(context(), "hub");
        permissionProperty().applyInput(context(), "uxmessentials.vip");

        assertThat(store.find("alpha"))
                .hasValueSatisfying(
                        a -> assertThat(a.condition()).isEqualTo("permission:uxmessentials.vip && world:hub"));
    }

    @Test
    void clearingTheWorldTargetLeavesThePermission() {
        store.save(StoredAnnouncement.fresh("alpha", "one").withCondition("permission:vip && world:hub"));

        worldProperty().applyInput(context(), "-");

        assertThat(store.find("alpha"))
                .hasValueSatisfying(a -> assertThat(a.condition()).isEqualTo("permission:vip"));
    }

    @Test
    void theSettingsScreenPersistsAndAppliesTheInterval() {
        // The interval property (settings slot 11) writes the typed seconds through the settings store; the override
        // then wins over the file default when the merge folds it in.
        EditableProperty interval = view.settings().propertyAt(11, new Object()).orElseThrow();
        assertThat(interval).isInstanceOf(TextProperty.class);

        ((TextProperty) interval).applyInput(context(), "150");

        assertThat(settingsStore.load().interval())
                .hasValueSatisfying(d -> assertThat(d.toSeconds()).isEqualTo(150L));
        // The persisted override replaces the file default interval through the domain fold.
        com.uxplima.uxmessentials.communication.domain.AnnouncerConfig file =
                new com.uxplima.uxmessentials.communication.domain.AnnouncerConfig(
                        java.time.Duration.ofMinutes(5),
                        0,
                        com.uxplima.uxmessentials.communication.domain.Ordering.SEQUENTIAL,
                        List.of(new com.uxplima.uxmessentials.communication.domain.Announcement(
                                "x",
                                List.of("line"),
                                com.uxplima.uxmessentials.shared.display.DisplayCondition.always(),
                                Optional.empty(),
                                java.util.Set.of(com.uxplima.uxmessentials.shared.display.BroadcastChannel.CHAT),
                                Optional.empty(),
                                false)));
        assertThat(settingsStore.load().applyTo(file).defaultInterval()).isEqualTo(java.time.Duration.ofSeconds(150));
    }

    @Test
    void theSettingsScreenClearsTheIntervalBackToTheFileDefault() {
        settingsStore.save(com.uxplima.uxmessentials.communication.domain.AnnouncerSettings.unset()
                .withIntervalSeconds(90));
        EditableProperty interval = view.settings().propertyAt(11, new Object()).orElseThrow();

        ((TextProperty) interval).applyInput(context(), "-");

        assertThat(settingsStore.load().interval()).isEmpty();
    }

    @Test
    void theSettingsScreenPersistsTheMinPlayersGate() {
        EditableProperty minPlayers =
                view.settings().propertyAt(15, new Object()).orElseThrow();

        ((TextProperty) minPlayers).applyInput(context(), "5");

        assertThat(settingsStore.load().minOnlinePlayers()).hasValue(5);
    }

    @Test
    void deleteIsGatedByAConfirmMenu() {
        store.save(StoredAnnouncement.fresh("alpha", "one"));
        view.editor().open(player, viewer, store.find("alpha").orElseThrow());

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the confirm menu, does not delete
        assertThat(store.exists("alpha")).isTrue();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button deletes
        assertThat(store.exists("alpha")).isFalse();
    }

    // --- helpers ---

    private TextProperty worldProperty() {
        return (TextProperty) view.editor()
                .propertyAt(WORLD_SLOT, store.find("alpha").orElseThrow())
                .orElseThrow();
    }

    private TextProperty permissionProperty() {
        return (TextProperty) view.editor()
                .propertyAt(PERMISSION_SLOT, store.find("alpha").orElseThrow())
                .orElseThrow();
    }

    private ClickContext context() {
        return new ClickContext(player, viewer, false, false, () -> {});
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView openView = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                openView, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    // --- fakes ---

    private static final class InMemoryAnnouncementStore implements AnnouncementStore {
        private final Map<String, StoredAnnouncement> rows = new ConcurrentHashMap<>();

        @Override
        public List<StoredAnnouncement> all() {
            return rows.values().stream()
                    .sorted(java.util.Comparator.comparing(StoredAnnouncement::id))
                    .toList();
        }

        @Override
        public List<StoredAnnouncement> enabled() {
            return all().stream().filter(StoredAnnouncement::enabled).toList();
        }

        @Override
        public Optional<StoredAnnouncement> find(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean exists(String id) {
            return rows.containsKey(id);
        }

        @Override
        public void save(StoredAnnouncement announcement) {
            rows.put(announcement.id(), announcement);
        }

        @Override
        public boolean delete(String id) {
            return rows.remove(id) != null;
        }
    }

    private static final class InMemoryAnnouncerSettingsStore
            implements com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore {
        private com.uxplima.uxmessentials.communication.domain.AnnouncerSettings settings =
                com.uxplima.uxmessentials.communication.domain.AnnouncerSettings.unset();

        @Override
        public com.uxplima.uxmessentials.communication.domain.AnnouncerSettings load() {
            return settings;
        }

        @Override
        public void save(com.uxplima.uxmessentials.communication.domain.AnnouncerSettings value) {
            settings = value;
        }
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
