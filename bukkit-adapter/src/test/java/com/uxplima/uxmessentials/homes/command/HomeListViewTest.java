package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionsLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorView;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /home} slot grid: opening it renders one filled cell per owned home and an
 * empty cell for each free slot up to the limit, clicking an empty cell creates a home there at the player's
 * position through the real {@link CreateHomeAtSlot}, and clicking a filled cell opens the per-home action menu.
 * The scheduler is a synchronous double so the entity-bound build runs inline, and uxmLib's menu listener is
 * installed against a mock plugin (reset on teardown) as the other GUI tests do.
 */
class HomeListViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final List<Integer> CELLS = List.of(10, 11, 12, 13, 14, 15, 16);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private FakeHomeRepository repository;
    private HomeListView listView;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        repository = new FakeHomeRepository();
        listView = listView();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void openingTheGridRendersFilledAndEmptyCells() {
        repository.put(home(0));
        repository.put(home(2));

        listView.open(player, viewer);

        Inventory grid = player.getOpenInventory().getTopInventory();
        assertThat(grid.getHolder()).isInstanceOf(SimpleGui.class);
        // The 3-home limit means slots 0..2 are addressable: cells 0 and 2 carry the filled bed, cell 1 the empty
        // bed, and the cells past the limit fall back to the filler pane.
        assertThat(grid.getItem(CELLS.get(0)).getType()).isEqualTo(org.bukkit.Material.RED_BED);
        assertThat(grid.getItem(CELLS.get(2)).getType()).isEqualTo(org.bukkit.Material.RED_BED);
        assertThat(grid.getItem(CELLS.get(1)).getType()).isEqualTo(org.bukkit.Material.GRAY_BED);
        assertThat(grid.getItem(CELLS.get(3)).getType()).isEqualTo(org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void clickingAnEmptyCellCreatesAHomeThere() {
        listView.open(player, viewer);

        fireClick(CELLS.get(1)); // the second cell maps to slot index 1, which is empty

        assertThat(repository.find(viewer, HomeSlot.of(1))).isPresent();
    }

    @Test
    void clickingAFilledCellOpensTheActionMenu() {
        repository.put(home(0));
        listView.open(player, viewer);

        fireClick(CELLS.get(0)); // the first cell holds the home in slot 0

        InventoryView open = player.getOpenInventory();
        assertThat(open.getTopInventory().getHolder()).isInstanceOf(SimpleGui.class);
        // The action menu is a fresh 27-slot (3-row) menu, distinct from the grid by its teleport button slot.
        assertThat(open.getTopInventory().getSize()).isEqualTo(27);
        assertThat(open.getTopInventory().getItem(11)).isNotNull();
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private Home home(int slot) {
        return Home.create(viewer, HomeSlot.of(slot), Position.of(WORLD, slot, 64, slot), Instant.EPOCH);
    }

    private HomeListView listView() {
        Messages messages = new KeyMessages();
        HomeNotifier notifier = new HomeNotifier(messages, new NoSink());
        DomainEventPublisher events = new NoEvents();
        Clock clock = Clock.system(ZoneOffset.UTC);
        HomeQuota quota = new HomeQuota(new AllowAllPermissions(), 3);
        Scheduler scheduler = new SyncScheduler();
        CreateHomeAtSlot create = new CreateHomeAtSlot(repository, quota, List.of(), notifier, events, 1000, clock);
        HomeActionView actionView = new HomeActionView(
                messages,
                scheduler,
                new TeleportHome(repository, new RecordingTeleporter(), notifier),
                new DeleteHome(repository, notifier, events),
                new RelocateHome(repository, notifier, events, clock),
                new RenameHome(repository, notifier, events, clock),
                new IconSelectorView(
                        messages,
                        scheduler,
                        new SetHomeIcon(repository, notifier, events, clock),
                        IconSelectorLayout.codeDefault()),
                new AnvilInput(plugin),
                HomeActionsLayout.codeDefault(),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC));
        return new HomeListView(
                messages,
                scheduler,
                new ListHomes(repository),
                quota,
                create,
                actionView,
                HomeListLayout.codeDefault(),
                1000,
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC));
    }

    /** A map-backed slot repository keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<Integer, Home>> byOwner = new ConcurrentHashMap<>();

        void put(Home home) {
            owned(home.owner()).put(home.slot().index(), home);
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, new ArrayList<>(owned(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return owned(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(owned(owner).get(slot.index()));
        }

        Optional<Home> find(PlayerRef owner, HomeSlot slot) {
            return findSlot(owner, slot);
        }

        @Override
        public void save(Home home) {
            put(home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            owned(owner).remove(slot.index());
        }

        private Map<Integer, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), u -> new java.util.TreeMap<>());
        }
    }

    private static final class RecordingTeleporter implements HomeTeleporter {
        @Override
        public void teleportTo(PlayerRef who, Home home) {}
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
