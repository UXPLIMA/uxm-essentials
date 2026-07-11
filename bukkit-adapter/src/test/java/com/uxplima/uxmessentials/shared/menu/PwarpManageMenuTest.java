package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpManageMenu;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.TransferPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.application.WithdrawEarnings;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ConfirmRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuTextPrompt;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The capability-gated management panel ({@code pwarp-manage}) the detail panel's manage button opens. Proves the
 * shipped spec loads and renders one warp's buttons over a real engine, that each button's capability gate hides it
 * from a role that lacks the capability, and that a click reaches the matching P4 use case: the access button cycles
 * through {@link EditPlayerWarp#setAccess}, a metadata edit threads its typed value through the engine's {@code input:}
 * step into {@link EditPlayerWarp#setDisplayName}, the bank reaches {@link WithdrawEarnings}, and the confirm-gated
 * delete reaches {@link ArchivePlayerWarp#archive}. The capability gate is proven both ways: a {@code MANAGER} viewer
 * never sees the price/transfer/delete buttons the {@code OWNER} does. Drives the façade through a synchronous scheduler
 * so the async resolve and the entity render run inline, and stands a recording prompt in for the text-input seam so a
 * password/name submit is driven by hand; the role is resolved through a real {@link WarpAuthorization}.
 */
class PwarpManageMenuTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    // The manage panel's fixed button slots, matching pwarp-manage.conf.
    private static final int INFO_SLOT = 4;
    private static final int RENAME_SLOT = 10;
    private static final int DISPLAY_NAME_SLOT = 11;
    private static final int ACCESS_SLOT = 19;
    private static final int PRICE_SLOT = 21;
    private static final int BANK_SLOT = 38;
    private static final int TRANSFER_SLOT = 40;
    private static final int SPONSOR_SLOT = 42;
    private static final int DELETE_SLOT = 53;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private PlayerRef otherOwner;
    private SyncScheduler scheduler;
    private RecordingPrompt prompt;

    private PlayerWarpRepository repository;
    private WarpMemberStore members;
    private EditPlayerWarp editPlayerWarp;
    private WithdrawEarnings withdrawEarnings;
    private TransferPlayerWarp transferPlayerWarp;
    private ArchivePlayerWarp archivePlayerWarp;
    private PlayerWarpManageMenu menu;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        otherOwner = new PlayerRef(UUID.randomUUID(), "Bob");
        scheduler = new SyncScheduler();
        repository = mock(PlayerWarpRepository.class);
        members = mock(WarpMemberStore.class);
        when(members.roleOf(any(), any())).thenReturn(Optional.empty());
        editPlayerWarp = mock(EditPlayerWarp.class);
        withdrawEarnings = mock(WithdrawEarnings.class);
        transferPlayerWarp = mock(TransferPlayerWarp.class);
        archivePlayerWarp = mock(ArchivePlayerWarp.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOwnerSeesEveryManagementButton() {
        Inventory inv = openAsOwner(WarpAccess.PUBLIC);

        assertThat(inv.getItem(INFO_SLOT).getType()).isEqualTo(Material.PAPER);
        assertThat(inv.getItem(RENAME_SLOT).getType()).isEqualTo(Material.NAME_TAG);
        assertThat(inv.getItem(DISPLAY_NAME_SLOT).getType()).isEqualTo(Material.WRITABLE_BOOK);
        assertThat(inv.getItem(ACCESS_SLOT).getType()).isEqualTo(Material.IRON_DOOR);
        assertThat(inv.getItem(PRICE_SLOT).getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(inv.getItem(BANK_SLOT).getType()).isEqualTo(Material.GOLD_BLOCK);
        assertThat(inv.getItem(TRANSFER_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(DELETE_SLOT).getType()).isEqualTo(Material.BARRIER);
        // The sponsor button waits on the P6 sub-feature flag, off for now, so it never renders yet.
        assertThat(inv.getItem(SPONSOR_SLOT).getType()).isEqualTo(FILLER);
    }

    @Test
    void aManagerDoesNotSeeThePriceTransferOrDeleteButtonsButTheOwnerDoes() {
        Inventory managerView = openAsManager(WarpAccess.PUBLIC);

        // A MANAGER holds only EDIT_METADATA (+ whitelist/bans, T4): presentation, never money or lifecycle.
        assertThat(managerView.getItem(DISPLAY_NAME_SLOT).getType()).isEqualTo(Material.WRITABLE_BOOK);
        assertThat(managerView.getItem(PRICE_SLOT).getType()).isEqualTo(FILLER);
        assertThat(managerView.getItem(TRANSFER_SLOT).getType()).isEqualTo(FILLER);
        assertThat(managerView.getItem(DELETE_SLOT).getType()).isEqualTo(FILLER);
        // RENAME is not a metadata capability, so a manager cannot rename either.
        assertThat(managerView.getItem(RENAME_SLOT).getType()).isEqualTo(FILLER);

        // The OWNER, resolved from ownership on a viewer-owned warp, sees exactly those three buttons.
        Inventory ownerView = openAsOwner(WarpAccess.PUBLIC);
        assertThat(ownerView.getItem(PRICE_SLOT).getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(ownerView.getItem(TRANSFER_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(ownerView.getItem(DELETE_SLOT).getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    void clickingAccessCyclesThroughSetAccess() {
        PlayerWarp warp = openedWarp(viewer, WarpAccess.PUBLIC);
        openAsOwner(warp);

        fireClick(ACCESS_SLOT, ClickType.LEFT);

        // PUBLIC is the first step of the cycle, so the next value is PASSWORD.
        verify(editPlayerWarp).setAccess(viewer, warp.name(), WarpAccess.PASSWORD);
    }

    @Test
    void editingTheDisplayNameThreadsTheTypedValueIntoSetDisplayName() {
        PlayerWarp warp = openedWarp(viewer, WarpAccess.PUBLIC);
        openAsOwner(warp);

        fireClick(DISPLAY_NAME_SLOT, ClickType.LEFT); // opens the input prompt, sets nothing yet
        assertThat(prompt.prompts).isEqualTo(1);

        prompt.submit("Cozy Base");

        verify(editPlayerWarp).setDisplayName(viewer, warp.name(), Optional.of(DisplayName.of("Cozy Base")));
    }

    @Test
    void clickingTheBankReachesWithdrawEarnings() {
        PlayerWarp warp = openedWarp(viewer, WarpAccess.PUBLIC);
        openAsOwner(warp);

        fireClick(BANK_SLOT, ClickType.LEFT);

        verify(withdrawEarnings).withdraw(viewer, warp.name());
    }

    @Test
    void deletingArchivesOnlyAfterTheConfirmIsAccepted() {
        PlayerWarp warp = openedWarp(viewer, WarpAccess.PUBLIC);
        openAsOwner(warp);

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the engine confirm; nothing archived yet
        verify(archivePlayerWarp, never()).archive(any(), any());

        fireClick(ConfirmRenderer.YES_SLOT, ClickType.LEFT); // confirm runs ArchivePlayerWarp (recoverable archive)

        verify(archivePlayerWarp).archive(viewer, warp.name());
    }

    // --- harness ---

    /** Seed a warp owned by the viewer (so the resolved role is OWNER) and open its manage panel. */
    private Inventory openAsOwner(WarpAccess access) {
        return openAsOwner(openedWarp(viewer, access));
    }

    private Inventory openAsOwner(PlayerWarp warp) {
        when(repository.findByName(warp.name())).thenReturn(Optional.of(warp));
        wireEngine();
        menu.open(viewer, warp.name());
        return top();
    }

    /** Seed a warp owned by another and stub the member store so the viewer resolves to a MANAGER, then open. */
    private Inventory openAsManager(WarpAccess access) {
        PlayerWarp warp = openedWarp(otherOwner, access);
        when(members.roleOf(any(), any())).thenReturn(Optional.of(WarpRole.MANAGER));
        when(repository.findByName(warp.name())).thenReturn(Optional.of(warp));
        wireEngine();
        menu.open(viewer, warp.name());
        return top();
    }

    private PlayerWarp openedWarp(PlayerRef owner, WarpAccess access) {
        return PlayerWarp.create(owner, owner.name(), PlayerWarpName.of("base"), at(), CLOCK.instant())
                .withId(PlayerWarpId.of(1))
                .withAccess(access, CLOCK.instant());
    }

    private void wireEngine() {
        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        Menus menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        prompt = new RecordingPrompt();
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener(),
                0L,
                System::currentTimeMillis,
                new PagedListSourceRegistry(),
                prompt);
        server.getPluginManager().registerEvents(listener, plugin);
        PlayerWarpNotifier notifier = new PlayerWarpNotifier(new KeyMessages(), noopSink());
        menu = new PlayerWarpManageMenu(
                menus,
                scheduler,
                repository,
                new WarpAuthorization(members),
                editPlayerWarp,
                withdrawEarnings,
                transferPlayerWarp,
                archivePlayerWarp,
                mock(PlayerLookup.class),
                new KeyMessages(),
                notifier,
                (ref, name) -> {});
        menu.register(bindings, dataFolder, NOOP);
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static Position at() {
        return Position.of(new WorldRef(UUID.randomUUID(), "world"), 0, 64, 0);
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    /** A synchronous stand-in for the text-input seam: it records the callbacks so the test fires submit by hand. */
    private static final class RecordingPrompt implements MenuTextPrompt {
        int prompts;

        @Nullable Consumer<String> onSubmit;

        @Override
        public void prompt(
                org.bukkit.entity.Player player,
                PlayerRef viewer,
                String key,
                Component promptLabel,
                @Nullable String initialText,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.prompts++;
            this.onSubmit = onSubmit;
        }

        void submit(String text) {
            java.util.Objects.requireNonNull(onSubmit, "onSubmit").accept(text);
        }
    }

    /** Surfaces the warp token so the info name renders as the warp name; every other key renders as its key path. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("pwarp.gui.manage.entry-name")) {
                return placeholders.getOrDefault("warp", "");
            }
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

    private static final class SyncScheduler implements com.uxplima.uxmessentials.shared.application.port.Scheduler {
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
