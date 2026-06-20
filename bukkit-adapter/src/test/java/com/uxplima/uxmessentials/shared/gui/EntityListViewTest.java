package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the shared {@link EntityListView}: the list is laid out entirely from a temp layout
 * conf (no hardcoded slots), renders one icon per fake entity into the conf's content slots, pages a list
 * longer than one page, invokes {@code onSelect} when an entity icon is clicked, and invokes {@code onCreate}
 * when the conf'd create button is clicked. The scheduler is a synchronous double so the entity-bound build
 * runs inline, and uxmLib's menu listener is installed against a mock plugin (reset on teardown).
 */
class EntityListViewTest {

    /** A tiny fake entity standing in for any module's managed type. */
    private record Widget(String name, Material icon) {}

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void rendersEntitiesAtTheConfContentSlots(@TempDir Path dir) throws Exception {
        EntityListLayout layout = layout(dir, "content-slots = [10, 11, 12]\ncreate-slot = -1\n");
        List<Widget> widgets = List.of(new Widget("a", Material.DIAMOND), new Widget("b", Material.GOLD_INGOT));
        view(layout, () -> widgets, (p, w) -> {}).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.DIAMOND);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.GOLD_INGOT);
        // A non-content, non-nav slot carries the configured glass filler.
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void clickingAnEntityInvokesOnSelect(@TempDir Path dir) throws Exception {
        EntityListLayout layout = layout(dir, "content-slots = [10, 11, 12]\ncreate-slot = -1\n");
        Widget target = new Widget("a", Material.DIAMOND);
        AtomicReference<Widget> selected = new AtomicReference<>();
        view(layout, () -> List.of(target), (p, w) -> selected.set(w)).open(player, viewer);

        fireClick(10);

        assertThat(selected.get()).isEqualTo(target);
    }

    @Test
    void pagesEntitiesBeyondOnePage(@TempDir Path dir) throws Exception {
        // Two content slots, three entities: the second page must show the third entity after a next-page click.
        EntityListLayout layout =
                layout(dir, "content-slots = [10, 11]\nnext-slot = 53\nprev-slot = 45\ncreate-slot = -1\n");
        List<Widget> widgets = List.of(
                new Widget("a", Material.DIAMOND),
                new Widget("b", Material.GOLD_INGOT),
                new Widget("c", Material.EMERALD));
        view(layout, () -> widgets, (p, w) -> {}).open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.DIAMOND);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.GOLD_INGOT);

        fireClick(53); // next page

        Inventory paged = player.getOpenInventory().getTopInventory();
        assertThat(paged.getItem(10).getType()).isEqualTo(Material.EMERALD);
        assertThat(paged.getItem(11)).isNull();
    }

    @Test
    void clickingTheCreateButtonInvokesOnCreate(@TempDir Path dir) throws Exception {
        EntityListLayout layout =
                layout(dir, "content-slots = [10, 11]\ncreate-slot = 49\ncreate-icon = \"EMERALD\"\n");
        AtomicBoolean created = new AtomicBoolean(false);
        EntityListView<Widget> view = EntityListView.<Widget>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(Key.TITLE)
                .navNames(Key.PREV, Key.NEXT)
                .entities(List::of)
                .iconRenderer((v, w) -> ItemBuilder.of(w.icon()).build())
                .onSelect((p, w) -> {})
                .onCreate(Key.CREATE, p -> created.set(true))
                .build();
        view.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(49).getType()).isEqualTo(Material.EMERALD);
        fireClick(49);

        assertThat(created).isTrue();
    }

    private EntityListView<Widget> view(
            EntityListLayout layout,
            java.util.function.Supplier<List<Widget>> entities,
            java.util.function.BiConsumer<org.bukkit.entity.Player, Widget> onSelect) {
        return EntityListView.<Widget>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(Key.TITLE)
                .navNames(Key.PREV, Key.NEXT)
                .entities(entities)
                .iconRenderer((v, w) -> ItemBuilder.of(w.icon()).build())
                .onSelect(onSelect)
                .build();
    }

    private EntityListLayout layout(Path dir, String body) throws Exception {
        Path file = dir.resolve("modules").resolve("demo").resolve("gui").resolve("demo-list.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "rows = 6\nnav-icon = \"ARROW\"\nfiller = \"GRAY_STAINED_GLASS_PANE\"\n" + body);
        return new GuiLayouts(dir, NOOP)
                .loadEntityList("demo", "demo-list", EntityListLayout.paginatedDefault(Material.CHEST));
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Catalog keys used by the test views; their text is irrelevant to slot/material assertions. */
    private enum Key implements MessageKey {
        TITLE("demo.list.title"),
        PREV("demo.list.prev"),
        NEXT("demo.list.next"),
        CREATE("demo.list.create");

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
