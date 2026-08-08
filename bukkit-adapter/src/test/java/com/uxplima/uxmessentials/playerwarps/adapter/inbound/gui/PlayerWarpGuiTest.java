package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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

import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.support.InMemoryPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.support.NoWarpMembers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the player-warps property editor. A number property (warmup) steps, clamps, and persists;
 * the visibility change persists through the same use case {@code /pwarp public} drives; and delete is
 * confirm-gated. The editor is laid out from a code-default layout (no hardcoded slots in the view), and the
 * scheduler runs every hop inline so the off-thread writes land synchronously. The list itself now renders through
 * the menu engine and is covered by {@code PlayerWarpListGoldenTest}. The lock and password controls were dropped
 * with the surrogate-id rebuild (they return richer as the P4 access gate), so they are no longer editor
 * properties and no longer covered here.
 */
class PlayerWarpGuiTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    // Editor property slots, in the order PlayerWarpEditorView builds its properties:
    // name, move, icon, visibility, dep-sound, arr-sound, dep-particle, arr-particle, warmup, cooldown.
    private static final List<Integer> EDITOR_SLOTS = List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22);
    private static final int ICON_SLOT = EDITOR_SLOTS.get(2);
    private static final int VISIBILITY_SLOT = EDITOR_SLOTS.get(3);
    private static final int WARMUP_SLOT = EDITOR_SLOTS.get(8);
    private static final int DELETE_SLOT = 53;
    private static final int CONFIRM_SLOT =
            11; // the engine confirm window's yes button slot (ConfirmRenderer.YES_SLOT)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private InMemoryPlayerWarpRepository repository;
    private PlayerWarpEditorView editorView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new InMemoryPlayerWarpRepository();
        Guis.install(plugin);

        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, new SilentSink());
        SetPlayerWarpVisibility visibility =
                new SetPlayerWarpVisibility(repository, notifier, java.time.Clock.systemUTC());
        ArchivePlayerWarp archivePlayerWarp = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(new NoWarpMembers()),
                notifier,
                event -> {},
                java.time.Clock.systemUTC());
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);

        EntityEditorLayout editorLayout = new EntityEditorLayout(
                6,
                EDITOR_SLOTS,
                49,
                java.util.OptionalInt.of(DELETE_SLOT),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        editorView = new PlayerWarpEditorView(
                editorEngine(guiText),
                guiText,
                scheduler,
                repository,
                visibility,
                archivePlayerWarp,
                textInput,
                messages,
                editorLayout,
                PlayerWarpEditorSubLayouts.codeDefault(),
                (p, v) -> {});
    }

    /**
     * One editor-capable engine plus its single listener: the editor opens through this {@link Menus} and its
     * visibility selector and delete-confirm children are routed by the one registered {@link MenuListener}, the
     * engine path the production wiring uses.
     */
    private Menus editorEngine(GuiText guiText) {
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
        return menus;
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void iconButtonWearsTheWarpsConfiguredMaterial() {
        store(viewer, "alpha");
        repository.save(warp("alpha").withIcon(Optional.of(IconSpec.of("DIAMOND")), Instant.EPOCH));
        editorView.open(player, viewer, owned("alpha"));

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(ICON_SLOT).getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void iconButtonFallsBackToItemFrameWhenUnset() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(ICON_SLOT).getType()).isEqualTo(Material.ITEM_FRAME);
    }

    @Test
    void numberPropertyStepsClampsAndPersists() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));
        assertThat(warp("alpha").timing().warmupSeconds()).isEmpty();

        fireClick(WARMUP_SLOT, ClickType.LEFT); // one whole second up (the step is 10 tenths)

        assertThat(warp("alpha").timing().warmupSeconds()).contains(1.0);

        // A right-click at zero stays clamped to "no override" rather than going negative.
        editorView.open(player, viewer, owned("alpha"));
        store(viewer, "beta");
        editorView.open(player, viewer, owned("beta"));
        fireClick(WARMUP_SLOT, ClickType.RIGHT);
        assertThat(warp("beta").timing().warmupSeconds()).isEmpty();
    }

    @Test
    void visibilityChangePersistsThroughTheUseCase() {
        store(viewer, "alpha");
        editorView.open(player, viewer, owned("alpha"));
        assertThat(warp("alpha").access()).isEqualTo(WarpAccess.PRIVATE);

        fireClick(VISIBILITY_SLOT, ClickType.LEFT); // opens the selector
        clickFirstSelectorOption(); // the first option (public) is selected

        assertThat(warp("alpha").access()).isEqualTo(WarpAccess.PUBLIC);
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

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the confirm menu, changes nothing
        assertThat(repository
                        .findByName(PlayerWarpName.of("alpha"))
                        .orElseThrow()
                        .status())
                .isEqualTo(WarpStatus.ACTIVE);

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button archives (recoverable, not a hard delete)
        assertThat(repository
                        .findByName(PlayerWarpName.of("alpha"))
                        .orElseThrow()
                        .status())
                .isEqualTo(WarpStatus.ARCHIVED);
    }

    // --- helpers ---

    private void store(PlayerRef owner, String name) {
        repository.save(
                PlayerWarp.create(owner, owner.name(), PlayerWarpName.of(name), AT, Instant.ofEpochMilli(1_000)));
    }

    private PlayerWarp warp(String name) {
        return repository.findByName(PlayerWarpName.of(name)).orElseThrow();
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

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
