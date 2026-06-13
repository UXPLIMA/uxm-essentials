package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

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
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
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
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the per-home action menu and its child icon picker. Button clicks are driven against the
 * live menu by their layout slot; the rename decision (blank/overlong/valid) is exercised through the extracted
 * {@code handleRenameInput} seam so the branch behaviour is pinned without driving a live anvil. The scheduler is a
 * synchronous double so the async-then-entity hops run inline, and feedback is captured through a recording
 * {@link MessageSink}.
 */
class HomeActionViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    // Default HomeActionsLayout slots: teleport 11, delete 13, relocate 15, changeIcon 16, back 22.
    private static final int TELEPORT_SLOT = 11;
    private static final int DELETE_SLOT = 13;
    private static final int RELOCATE_SLOT = 15;
    private static final int CHANGE_ICON_SLOT = 16;
    private static final int BACK_SLOT = 22;
    // Default IconSelectorLayout: 6 rows, content fills slots 0..44 (RED_BED first), reset 45, prev 48, back 49,
    // next 50.
    private static final int ICON_FIRST_CELL = 0;
    private static final int ICON_RESET_SLOT = 45;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private FakeHomeRepository repository;
    private RecordingTeleporter teleporter;
    private RecordingSink sink;
    private TogglePermissions permissions;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        repository = new FakeHomeRepository();
        teleporter = new RecordingTeleporter();
        sink = new RecordingSink();
        permissions = new TogglePermissions(true);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void teleportButtonDelegatesToTheTeleporter() {
        Home home = seed(home(0));
        open(home);

        fireClick(TELEPORT_SLOT);

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void deleteButtonRemovesTheSlot() {
        Home home = seed(home(0));
        open(home);

        fireClick(DELETE_SLOT);

        assertThat(repository.findSlot(viewer, HomeSlot.of(0))).isEmpty();
    }

    @Test
    void relocateButtonReanchorsTheHome() {
        Home home = seed(home(0)); // seeded at x=0,z=0
        open(home);
        player.setLocation(new org.bukkit.Location(server.getWorlds().get(0), 100, 70, 200));

        fireClick(RELOCATE_SLOT);

        Home moved = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(moved.location().blockX()).isEqualTo(100);
        assertThat(moved.location().blockZ()).isEqualTo(200);
    }

    @Test
    void backButtonReopensTheListWithoutMutating() {
        Home home = seed(home(0));
        boolean[] reopened = {false};
        view().open(player, viewer, home, () -> reopened[0] = true);

        fireClick(BACK_SLOT);

        assertThat(reopened[0]).isTrue();
        assertThat(repository.findSlot(viewer, HomeSlot.of(0))).isPresent();
    }

    @Test
    void changeIconButtonIsPlacedAndOpensTheSelectorWithThePermission() {
        Home home = seed(home(0));
        open(home);

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(CHANGE_ICON_SLOT)).isNotNull();
        assertThat(menu.getItem(CHANGE_ICON_SLOT).getType()).isEqualTo(Material.ITEM_FRAME);

        fireClick(CHANGE_ICON_SLOT);

        Inventory selector = player.getOpenInventory().getTopInventory();
        assertThat(selector.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(selector.getItem(ICON_RESET_SLOT)).isNotNull();
        assertThat(selector.getItem(ICON_RESET_SLOT).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    void changeIconButtonIsAbsentWithoutThePermission() {
        permissions = new TogglePermissions(false);
        Home home = seed(home(0));
        open(home);

        Inventory menu = player.getOpenInventory().getTopInventory();
        // The slot falls back to the border filler when the change-icon button is gated off.
        assertThat(menu.getItem(CHANGE_ICON_SLOT).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void validRenameInputRenamesTheHome() {
        Home home = seed(home(0).withLabel(Optional.of(HomeLabel.of("Base")), Instant.EPOCH));

        view().handleRenameInput(player, viewer, home, () -> {}, "Castle");

        Home renamed = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(renamed.label()).map(HomeLabel::value).contains("Castle");
        assertThat(sink.delivered).contains(HomesMessageKey.HOME_RENAMED.key());
    }

    @Test
    void blankRenameInputDoesNotRenameAndKeepsTheLabel() {
        Home home = seed(home(0).withLabel(Optional.of(HomeLabel.of("Base")), Instant.EPOCH));

        view().handleRenameInput(player, viewer, home, () -> {}, "   ");

        Home unchanged = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(unchanged.label()).map(HomeLabel::value).contains("Base");
        assertThat(sink.delivered).contains(HomesMessageKey.HOME_RENAME_TOO_LONG.key());
        assertThat(sink.delivered).doesNotContain(HomesMessageKey.HOME_RENAMED.key());
    }

    @Test
    void overlongRenameInputDoesNotRenameAndKeepsTheLabel() {
        Home home = seed(home(0).withLabel(Optional.of(HomeLabel.of("Base")), Instant.EPOCH));
        String tooLong = "x".repeat(HomeLabel.MAX_LENGTH + 1);

        view().handleRenameInput(player, viewer, home, () -> {}, tooLong);

        Home unchanged = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(unchanged.label()).map(HomeLabel::value).contains("Base");
        assertThat(sink.delivered).contains(HomesMessageKey.HOME_RENAME_TOO_LONG.key());
        assertThat(sink.delivered).doesNotContain(HomesMessageKey.HOME_RENAMED.key());
    }

    @Test
    void iconSelectorPickSetsTheChosenMaterial() {
        Home home = seed(home(0));
        open(home);
        fireClick(CHANGE_ICON_SLOT); // open the selector

        fireClick(ICON_FIRST_CELL); // the first palette cell is RED_BED

        Home iconed = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(iconed.icon()).map(HomeIcon::materialName).contains(Material.RED_BED.name());
    }

    @Test
    void iconSelectorResetClearsTheCustomIcon() {
        Home home = seed(home(0).withIcon(Optional.of(HomeIcon.of("DIAMOND_BLOCK")), Instant.EPOCH));
        open(home);
        fireClick(CHANGE_ICON_SLOT); // open the selector

        fireClick(ICON_RESET_SLOT);

        Home cleared = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(cleared.icon()).isEmpty();
    }

    private void open(Home home) {
        view().open(player, viewer, home, () -> {});
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private Home seed(Home home) {
        repository.put(home);
        return home;
    }

    private Home home(int slot) {
        return Home.create(viewer, HomeSlot.of(slot), Position.of(WORLD, slot, 64, slot), Instant.EPOCH);
    }

    private HomeActionView view() {
        Messages messages = new KeyMessages();
        HomeNotifier notifier = new HomeNotifier(messages, sink);
        DomainEventPublisher events = new NoEvents();
        Clock clock = Clock.system(ZoneOffset.UTC);
        Scheduler scheduler = new SyncScheduler();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
        IconSelectorView iconSelector = new IconSelectorView(
                messages,
                scheduler,
                new SetHomeIcon(repository, notifier, events, clock),
                IconSelectorLayout.codeDefault());
        return new HomeActionView(
                messages,
                notifier,
                permissions,
                scheduler,
                new TeleportHome(repository, teleporter, notifier),
                new DeleteHome(repository, notifier, events),
                new RelocateHome(repository, notifier, events, clock),
                new RenameHome(repository, notifier, events, clock),
                iconSelector,
                new AnvilInput(plugin),
                HomeActionsLayout.codeDefault(),
                fmt);
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
        int hops;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            hops++;
        }
    }

    /** Captures every resolved message string the notifier delivers. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new CopyOnWriteArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
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

    /** A permission fake whose single {@code has} answer is flipped per test. */
    private static final class TogglePermissions implements Permissions {
        private final boolean granted;

        TogglePermissions(boolean granted) {
            this.granted = granted;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
