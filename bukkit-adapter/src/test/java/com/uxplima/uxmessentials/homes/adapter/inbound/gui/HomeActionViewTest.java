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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

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
 * MockBukkit coverage of the per-home action menu and its child icon picker. Button clicks are driven against the
 * live menu by their layout slot; the rename decision (blank/overlong/valid) is exercised through the extracted
 * {@code handleRenameInput} seam so the branch behaviour is pinned without driving a live anvil. The confirm-dialog
 * decisions (delete, relocate, unsafe-teleport) are exercised through the extracted {@code handleDelete},
 * {@code handleRelocate} and {@code handleTeleport} seams with a {@link RecordingPrompt} so the confirm/direct
 * branches are covered without opening a live {@code ConfirmMenu} inventory. The scheduler is a synchronous double
 * so the async-then-entity hops run inline, and feedback is captured through a recording {@link MessageSink}.
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

    @Nullable private SyncScheduler scheduler; // assigned by viewWith(...) on each build

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

    // --- confirm-delete decision seam ---

    @Test
    void deleteWithConfirmOnOpensConfirmDialogWithoutDeleting() {
        Home home = seed(home(0));
        RecordingPrompt prompt = new RecordingPrompt();

        viewWith(true, false, false, false, pos -> false).handleDelete(player, viewer, home, () -> {}, prompt);

        assertThat(prompt.prompted).isTrue();
        // Home must still exist — confirm was not triggered.
        assertThat(repository.findSlot(viewer, HomeSlot.of(0))).isPresent();
    }

    @Test
    void deleteWithConfirmOnDeletesWhenConfirmed() {
        Home home = seed(home(0));
        RecordingPrompt prompt = RecordingPrompt.autoConfirm();

        viewWith(true, false, false, false, pos -> false).handleDelete(player, viewer, home, () -> {}, prompt);

        assertThat(repository.findSlot(viewer, HomeSlot.of(0))).isEmpty();
    }

    @Test
    void deleteWithConfirmOnKeepsHomeWhenCancelled() {
        Home home = seed(home(0));
        RecordingPrompt prompt = RecordingPrompt.autoCancel();

        viewWith(true, false, false, false, pos -> false).handleDelete(player, viewer, home, () -> {}, prompt);

        assertThat(repository.findSlot(viewer, HomeSlot.of(0))).isPresent();
    }

    @Test
    void deleteWithConfirmOffDeletesDirectly() {
        Home home = seed(home(0));
        RecordingPrompt prompt = new RecordingPrompt();

        viewWith(false, false, false, false, pos -> false).handleDelete(player, viewer, home, () -> {}, prompt);

        assertThat(prompt.prompted).isFalse();
        assertThat(repository.findSlot(viewer, HomeSlot.of(0))).isEmpty();
    }

    // --- confirm-relocate decision seam ---

    @Test
    void relocateWithConfirmOnOpensConfirmDialogWithoutMoving() {
        Home home = seed(home(0));
        player.setLocation(new org.bukkit.Location(server.getWorlds().get(0), 100, 70, 200));
        RecordingPrompt prompt = new RecordingPrompt();

        viewWith(false, true, false, false, pos -> false).handleRelocate(player, viewer, home, () -> {}, prompt);

        assertThat(prompt.prompted).isTrue();
        // Still at original coords — confirm was not triggered.
        Home unchanged = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(unchanged.location().blockX()).isEqualTo(0);
    }

    @Test
    void relocateWithConfirmOnMovesWhenConfirmed() {
        Home home = seed(home(0));
        player.setLocation(new org.bukkit.Location(server.getWorlds().get(0), 100, 70, 200));
        RecordingPrompt prompt = RecordingPrompt.autoConfirm();

        viewWith(false, true, false, false, pos -> false).handleRelocate(player, viewer, home, () -> {}, prompt);

        Home moved = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(moved.location().blockX()).isEqualTo(100);
    }

    @Test
    void relocateWithConfirmOffMovesDirectly() {
        Home home = seed(home(0));
        player.setLocation(new org.bukkit.Location(server.getWorlds().get(0), 55, 64, 55));
        RecordingPrompt prompt = new RecordingPrompt();

        viewWith(false, false, false, false, pos -> false).handleRelocate(player, viewer, home, () -> {}, prompt);

        assertThat(prompt.prompted).isFalse();
        Home moved = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(moved.location().blockX()).isEqualTo(55);
    }

    @Test
    void relocateToUnsafeSpotIsRejectedWithoutMoving() {
        Home home = seed(home(0)); // seeded at x=0,z=0
        player.setLocation(new org.bukkit.Location(server.getWorlds().get(0), 100, 70, 200));
        RecordingPrompt prompt = new RecordingPrompt();

        // block-unsafe-relocate on, unsafe predicate, bypass denied → reject before persisting.
        permissions = new TogglePermissions(false);
        viewWith(false, false, false, true, pos -> true).handleRelocate(player, viewer, home, () -> {}, prompt);

        Home unchanged = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(unchanged.location().blockX()).isEqualTo(0);
        assertThat(sink.delivered).contains(HomesMessageKey.HOME_UNSAFE_LOCATION.key());
    }

    @Test
    void relocateReadsSafetyOnTheTargetRegion() {
        Home home = seed(home(0));
        player.setLocation(new org.bukkit.Location(server.getWorlds().get(0), 100, 70, 200));
        RecordingPrompt prompt = new RecordingPrompt();

        HomeActionView view = viewWith(false, false, false, true, pos -> false);
        view.handleRelocate(player, viewer, home, () -> {}, prompt);

        SyncScheduler used = Objects.requireNonNull(scheduler);
        assertThat(used.regionHops).anyMatch(p -> p.blockX() == 100 && p.blockY() == 70 && p.blockZ() == 200);
        Home moved = repository.findSlot(viewer, HomeSlot.of(0)).orElseThrow();
        assertThat(moved.location().blockX()).isEqualTo(100);
    }

    // --- confirm-unsafe-teleport decision seam ---

    @Test
    void teleportToUnsafeDestinationWithConfirmOnOpensConfirmDialog() {
        Home home = seed(home(0));
        open(home);
        Inventory gui = player.getOpenInventory().getTopInventory();
        RecordingPrompt prompt = new RecordingPrompt();

        // Unsafe predicate returns true; confirm toggle is on; permissions deny bypass.
        permissions = new TogglePermissions(false);
        viewWith(false, false, true, false, pos -> true)
                .handleTeleport(player, viewer, home, (SimpleGui) gui.getHolder(), prompt);

        assertThat(prompt.prompted).isTrue();
        assertThat(teleporter.hops).isEqualTo(0);
    }

    @Test
    void teleportToUnsafeDestinationWithConfirmOnTeleportsWhenConfirmed() {
        Home home = seed(home(0));
        open(home);
        Inventory gui = player.getOpenInventory().getTopInventory();
        RecordingPrompt prompt = RecordingPrompt.autoConfirm();

        permissions = new TogglePermissions(false);
        viewWith(false, false, true, false, pos -> true)
                .handleTeleport(player, viewer, home, (SimpleGui) gui.getHolder(), prompt);

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void teleportToUnsafeDestinationWithBypassPermissionTeleportsDirectly() {
        Home home = seed(home(0));
        open(home);
        Inventory gui = player.getOpenInventory().getTopInventory();
        RecordingPrompt prompt = new RecordingPrompt();

        // All permissions granted (includes bypass.unsafe).
        permissions = new TogglePermissions(true);
        viewWith(false, false, true, false, pos -> true)
                .handleTeleport(player, viewer, home, (SimpleGui) gui.getHolder(), prompt);

        assertThat(prompt.prompted).isFalse();
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void teleportToSafeDestinationWithConfirmOnTeleportsDirectly() {
        Home home = seed(home(0));
        open(home);
        Inventory gui = player.getOpenInventory().getTopInventory();
        RecordingPrompt prompt = new RecordingPrompt();

        permissions = new TogglePermissions(false);
        // Safe predicate returns false — no confirm needed.
        viewWith(false, false, true, false, pos -> false)
                .handleTeleport(player, viewer, home, (SimpleGui) gui.getHolder(), prompt);

        assertThat(prompt.prompted).isFalse();
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void teleportWithUnsafeConfirmToggleOffTeleportsDirectly() {
        Home home = seed(home(0));
        open(home);
        Inventory gui = player.getOpenInventory().getTopInventory();
        RecordingPrompt prompt = new RecordingPrompt();

        permissions = new TogglePermissions(false);
        // Toggle is off — even with unsafe destination no dialog is shown.
        viewWith(false, false, false, false, pos -> true)
                .handleTeleport(player, viewer, home, (SimpleGui) gui.getHolder(), prompt);

        assertThat(prompt.prompted).isFalse();
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void unsafeTeleportCheckReadsTheDestinationRegion() {
        // The home (its location) sits at slot 0 → x=0,z=0,y=64 in the seeded WORLD.
        Home home = seed(home(0));
        open(home);
        Inventory gui = player.getOpenInventory().getTopInventory();
        RecordingPrompt prompt = new RecordingPrompt();

        permissions = new TogglePermissions(false);
        HomeActionView view = viewWith(false, false, true, false, pos -> true);
        view.handleTeleport(player, viewer, home, (SimpleGui) gui.getHolder(), prompt);

        // The safety read was scheduled on the destination home's region, not the clicking entity thread.
        SyncScheduler used = Objects.requireNonNull(scheduler);
        assertThat(used.regionHops)
                .anyMatch(p -> p.world().equals(home.location().world())
                        && p.blockX() == home.location().blockX()
                        && p.blockZ() == home.location().blockZ());
    }

    // --- helpers ---

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

    /** Build a view with all confirm toggles off and a "safe" destination predicate (existing tests unchanged). */
    private HomeActionView view() {
        return viewWith(false, false, false, false, pos -> false);
    }

    private HomeActionView viewWith(
            boolean confirmDelete,
            boolean confirmRelocate,
            boolean confirmUnsafeTeleport,
            boolean blockUnsafeRelocate,
            Predicate<Position> destinationUnsafe) {
        Messages messages = new KeyMessages();
        HomeNotifier notifier = new HomeNotifier(messages, sink);
        DomainEventPublisher events = new NoEvents();
        Clock clock = Clock.system(ZoneOffset.UTC);
        scheduler = new SyncScheduler();
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
                new RelocateHome(repository, List.of(), notifier, events, clock),
                new RenameHome(repository, notifier, events, clock),
                iconSelector,
                new AnvilInput(plugin),
                HomeActionsLayout.codeDefault(),
                fmt,
                confirmDelete,
                confirmRelocate,
                confirmUnsafeTeleport,
                blockUnsafeRelocate,
                destinationUnsafe);
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

    /**
     * Runs every hop inline while recording the positions handed to {@link #onRegion}, so a test can prove
     * the block-safety read is scheduled on the destination's region rather than the calling entity thread.
     */
    private static final class SyncScheduler implements Scheduler {
        private final List<Position> regionHops = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            regionHops.add(position);
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

    /**
     * A {@link HomeActionView.ConfirmPrompt} that records whether it was invoked and optionally
     * auto-fires one of the decision callbacks for testing confirmed/cancelled branches.
     */
    private static final class RecordingPrompt implements HomeActionView.ConfirmPrompt {
        boolean prompted;
        private final @Nullable Boolean autoDecision;

        RecordingPrompt() {
            this.autoDecision = null;
        }

        private RecordingPrompt(Boolean autoDecision) {
            this.autoDecision = autoDecision;
        }

        /** Auto-fires the {@code onConfirm} callback immediately when prompted. */
        static RecordingPrompt autoConfirm() {
            return new RecordingPrompt(Boolean.TRUE);
        }

        /** Auto-fires the {@code onCancel} callback immediately when prompted. */
        static RecordingPrompt autoCancel() {
            return new RecordingPrompt(Boolean.FALSE);
        }

        @Override
        public void prompt(Component title, Runnable onConfirm, Runnable onCancel) {
            prompted = true;
            if (Boolean.TRUE.equals(autoDecision)) {
                onConfirm.run();
            } else if (Boolean.FALSE.equals(autoDecision)) {
                onCancel.run();
            }
        }
    }
}
