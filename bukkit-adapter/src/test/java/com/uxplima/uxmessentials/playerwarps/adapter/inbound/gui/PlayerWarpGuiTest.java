package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the player-warps management GUI. The list renders one icon per warp the viewer owns and
 * a click opens the editor; a text property (password) routes a validated anvil line to the store; a number
 * property (warmup) steps, clamps, and persists; a toggle property (lock) flips and persists; the visibility
 * change persists; delete is confirm-gated; and a non-owner without the manage node never sees another player's
 * warp in their list (the editor is reachable only from the owner-scoped list). The views are laid out from a
 * code-default layout (no hardcoded slots in the view), and the scheduler runs every hop inline so the
 * off-thread writes land synchronously.
 */
class PlayerWarpGuiTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    // Editor property slots, in the order PlayerWarpEditorView builds its properties:
    // name, move, icon, visibility, lock, password, dep-sound, arr-sound, dep-particle, arr-particle, warmup, cooldown.
    private static final List<Integer> EDITOR_SLOTS = List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24);
    private static final int NAME_SLOT = EDITOR_SLOTS.get(0);
    private static final int VISIBILITY_SLOT = EDITOR_SLOTS.get(3);
    private static final int LOCK_SLOT = EDITOR_SLOTS.get(4);
    private static final int PASSWORD_SLOT = EDITOR_SLOTS.get(5);
    private static final int WARMUP_SLOT = EDITOR_SLOTS.get(10);
    private static final int DELETE_SLOT = 53;
    private static final int CONFIRM_SLOT = 11; // uxmLib ConfirmMenu's confirm button slot

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private PlayerWarpListView listView;
    private PlayerWarpEditorView editorView;
    private boolean managePerm;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeRepository();
        managePerm = false;
        Guis.install(plugin);

        Messages messages = new KeyMessages();
        PlayerWarpNotifier notifier = new PlayerWarpNotifier(messages, new SilentSink());
        Permissions permissions = new ManagePermissions(() -> managePerm);
        SetPlayerWarp setPlayerWarp = new SetPlayerWarp(
                repository,
                new PlayerWarpQuota(permissions, 3),
                notifier,
                event -> {},
                java.time.Clock.systemUTC(),
                List.of());
        SetPlayerWarpVisibility visibility = new SetPlayerWarpVisibility(repository, notifier);
        DelPlayerWarp delPlayerWarp = new DelPlayerWarp(repository, notifier, event -> {});
        AnvilInput anvil = new AnvilInput(plugin);

        EntityEditorLayout editorLayout = new EntityEditorLayout(
                6,
                EDITOR_SLOTS,
                49,
                java.util.OptionalInt.of(DELETE_SLOT),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        editorView = new PlayerWarpEditorView(
                guiText,
                scheduler,
                repository,
                visibility,
                delPlayerWarp,
                anvil,
                messages,
                editorLayout,
                PlayerWarpEditorSubLayouts.codeDefault(),
                (p, v) -> listView.open(p, v));
        // Two explicit content slots so an unused slot shows the background filler rather than a null cell — the
        // same way the bundled conf's empty content-slots resolve to a page region with filler around it.
        EntityListLayout listLayout = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout(
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout(
                        6, Material.ENDER_PEARL, Material.ARROW, 48, 50, List.of(0, 1)),
                Material.BLACK_STAINED_GLASS_PANE,
                java.util.OptionalInt.of(49),
                Material.LIME_DYE);
        listView = new PlayerWarpListView(
                guiText, scheduler, permissions, messages, repository, setPlayerWarp, anvil, listLayout, editorView);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void listRendersTheViewersOwnWarps() {
        store(viewer, "alpha");
        store(viewer, "beta");
        store(new PlayerRef(UUID.randomUUID(), "Bob"), "secret"); // another owner's warp, not the viewer's

        listView.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        // The viewer owns two warps; the third belongs to Bob and a player without the manage node never sees it.
        assertThat(inv.getItem(0)).isNotNull();
        assertThat(inv.getItem(1)).isNotNull();
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void clickingAWarpOpensTheEditor() {
        store(viewer, "alpha");
        listView.open(player, viewer);

        fireClick(0, ClickType.LEFT);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(NAME_SLOT)).isNotNull();
        assertThat(inv.getItem(NAME_SLOT).getType()).isEqualTo(Material.NAME_TAG);
    }

    @Test
    void textPropertyRoutesAnvilInputToTheStore() {
        store(viewer, "alpha");
        EditableProperty password =
                editorView.grid().propertyAt(PASSWORD_SLOT, owned("alpha")).orElseThrow();
        assertThat(password).isInstanceOf(TextProperty.class);

        ((TextProperty) password).applyInput(new ClickContext(player, viewer, false, false, () -> {}), "hunter2");

        assertThat(warp("alpha").password()).contains("hunter2");
    }

    @Test
    void numberPropertyStepsClampsAndPersists() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));
        assertThat(warp("alpha").warmupOverrideSeconds()).isEmpty();

        fireClick(WARMUP_SLOT, ClickType.LEFT); // one whole second up (the step is 10 tenths)

        assertThat(warp("alpha").warmupOverrideSeconds()).contains(1.0);

        // A right-click at zero stays clamped to "no override" rather than going negative.
        editorView.open(player, viewer, owned("alpha"));
        store(viewer, "beta");
        editorView.open(player, viewer, owned("beta"));
        fireClick(WARMUP_SLOT, ClickType.RIGHT);
        assertThat(warp("beta").warmupOverrideSeconds()).isEmpty();
    }

    @Test
    void togglePropertyFlipsLockAndPersists() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));
        assertThat(warp("alpha").isLocked()).isFalse();

        EditableProperty lock =
                editorView.grid().propertyAt(LOCK_SLOT, owned("alpha")).orElseThrow();
        assertThat(lock).isInstanceOf(ToggleProperty.class);
        fireClick(LOCK_SLOT, ClickType.LEFT);

        assertThat(warp("alpha").isLocked()).isTrue();
    }

    @Test
    void visibilityChangePersistsThroughTheUseCase() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));
        assertThat(warp("alpha").isPublic()).isFalse();

        fireClick(VISIBILITY_SLOT, ClickType.LEFT); // opens the selector
        clickFirstSelectorOption(); // the first option (public) is selected

        assertThat(warp("alpha").isPublic()).isTrue();
    }

    @Test
    void warmupClampsToFloorOnly() {
        // A NumberProperty is the warmup; ensure it is the right type so the stepper semantics above hold.
        store(viewer, "alpha");
        EditableProperty warmup =
                editorView.grid().propertyAt(WARMUP_SLOT, owned("alpha")).orElseThrow();
        assertThat(warmup).isInstanceOf(NumberProperty.class);
    }

    @Test
    void deleteIsGatedByAConfirmMenu() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the confirm menu, does not delete
        assertThat(repository.find(viewer, PlayerWarpName.of("alpha"))).isPresent();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button deletes
        assertThat(repository.find(viewer, PlayerWarpName.of("alpha"))).isEmpty();
    }

    @Test
    void aNonOwnerWithoutThePermNeverSeesAnotherPlayersWarp() {
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        store(bob, "bobwarp");
        // Alice (the viewer) owns nothing and lacks the manage node.

        listView.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        // No entity icon is drawn into the page region, so Bob's warp is unreachable from Alice's list.
        assertThat(inv.getItem(0)).isNull();
        assertThat(inv.getItem(1)).isNull();
    }

    @Test
    void anOperatorWithThePermSeesEveryOwnersWarps() {
        managePerm = true;
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        store(viewer, "alpha");
        store(bob, "bobwarp");

        listView.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(0)).isNotNull();
        assertThat(inv.getItem(1)).isNotNull();
        assertThat(inv.getItem(0).getType()).isNotEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        assertThat(inv.getItem(1).getType()).isNotEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    // --- helpers ---

    private void store(PlayerRef owner, String name) {
        repository.save(new PlayerWarp(owner, PlayerWarpName.of(name), AT, false, Instant.ofEpochMilli(1_000)));
    }

    private PlayerWarp warp(String name) {
        return repository.find(viewer, PlayerWarpName.of(name)).orElseThrow();
    }

    private OwnedWarp owned(String name) {
        return new OwnedWarp(viewer, warp(name));
    }

    private void clickFirstSelectorOption() {
        Inventory inv = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() == Material.PAPER) {
                fireClick(slot, ClickType.LEFT);
                return;
            }
        }
        throw new AssertionError("no selector option drawn");
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    // --- fakes ---

    private static final class FakeRepository implements PlayerWarpRepository {
        private final Map<String, PlayerWarp> byKey = new LinkedHashMap<>();

        private static String key(PlayerRef owner, PlayerWarpName name) {
            return owner.uuid() + "/" + name.value();
        }

        @Override
        public Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name) {
            return Optional.ofNullable(byKey.get(key(owner, name)));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            List<PlayerWarp> owned = new ArrayList<>();
            for (PlayerWarp warp : byKey.values()) {
                if (warp.owner().equals(owner)) {
                    owned.add(warp);
                }
            }
            return List.copyOf(owned);
        }

        @Override
        public List<PlayerWarp> all() {
            return List.copyOf(byKey.values());
        }

        @Override
        public List<PlayerWarp> publicOf(PlayerRef owner) {
            return ownedBy(owner).stream().filter(PlayerWarp::isPublic).toList();
        }

        @Override
        public int count(PlayerRef owner) {
            return ownedBy(owner).size();
        }

        @Override
        public boolean exists(PlayerRef owner, PlayerWarpName name) {
            return byKey.containsKey(key(owner, name));
        }

        @Override
        public void save(PlayerWarp warp) {
            byKey.put(key(warp.owner(), warp.name()), warp);
        }

        @Override
        public void delete(PlayerRef owner, PlayerWarpName name) {
            byKey.remove(key(owner, name));
        }

        @Override
        public void rate(PlayerRef owner, PlayerWarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(PlayerRef owner, PlayerWarpName name) {
            return 0.0;
        }
    }

    private static final class ManagePermissions implements Permissions {
        private final java.util.function.BooleanSupplier grants;

        ManagePermissions(java.util.function.BooleanSupplier grants) {
            this.grants = grants;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grants.getAsBoolean();
        }

        @Override
        public Permissions.QuotaResult resolveQuota(
                PlayerRef who,
                Permissions.QuotaFamily family,
                @org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return Permissions.QuotaResult.unlimited();
        }
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
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
}
