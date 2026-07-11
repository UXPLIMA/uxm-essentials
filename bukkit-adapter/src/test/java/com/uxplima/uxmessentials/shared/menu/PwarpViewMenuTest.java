package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpViewMenu;
import com.uxplima.uxmessentials.playerwarps.application.FavouritePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.RatePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuTextPrompt;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * The per-warp detail panel ({@code pwarp-view}) a browse tile opens, and the five-star rating menu
 * ({@code pwarp-rate}) behind it. Proves the shipped specs load and render one warp's buttons over a real engine, and
 * that each button reaches the use case with the parsed arguments: teleport goes through {@link UsePlayerWarp} with no
 * password on an open card and with the typed line on a PASSWORD card (prompted through the engine's {@code input:}
 * step), favourite through {@link FavouritePlayerWarp}, and a star through {@link RatePlayerWarp}; and the rate button
 * is hidden for the warp's owner. Drives the façade through a synchronous scheduler so the async resolve and the entity
 * render both run inline, and stands a recording prompt in for the text-input seam so the password submit is driven by
 * hand.
 */
class PwarpViewMenuTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private PlayerRef otherOwner;
    private SyncScheduler scheduler;
    private RecordingPrompt prompt;

    private PlayerWarpRepository repository;
    private WarpFavouriteStore favourites;
    private WarpMemberStore members;
    private UsePlayerWarp usePlayerWarp;
    private FavouritePlayerWarp favouritePlayerWarp;
    private RatePlayerWarp ratePlayerWarp;
    private PlayerWarpViewMenu menu;
    private final List<PlayerWarpName> managed = new ArrayList<>();

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
        favourites = mock(WarpFavouriteStore.class);
        members = mock(WarpMemberStore.class);
        when(members.roleOf(any(), any())).thenReturn(Optional.empty());
        usePlayerWarp = mock(UsePlayerWarp.class);
        favouritePlayerWarp = mock(FavouritePlayerWarp.class);
        ratePlayerWarp = mock(RatePlayerWarp.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theDetailPanelRendersTheInfoTileAndTheActionButtons() {
        Inventory inv = openView(warp(otherOwner, WarpAccess.PUBLIC));

        assertThat(inv.getItem(4).getType()).isEqualTo(Material.PAPER); // read-only info tile
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.ENDER_PEARL); // teleport (open card)
        assertThat(inv.getItem(13).getType()).isEqualTo(Material.NETHER_STAR); // favourite (not yet starred)
        assertThat(inv.getItem(15).getType()).isEqualTo(Material.EXPERIENCE_BOTTLE); // rate (viewer is not the owner)
    }

    @Test
    void clickingTeleportOnAnOpenWarpReachesUsePlayerWarpWithNoPassword() {
        PlayerWarp warp = warp(otherOwner, WarpAccess.PUBLIC);
        openView(warp);

        fireClick(11, ClickType.LEFT); // the open teleport button

        verify(usePlayerWarp).useFor(viewer, warp.name(), Optional.empty());
    }

    @Test
    void clickingTeleportOnAPasswordWarpPromptsThenThreadsTheTypedPassword() {
        PlayerWarp warp = warp(otherOwner, WarpAccess.PASSWORD);
        Inventory inv = openView(warp);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.ENDER_EYE); // the locked teleport button

        fireClick(11, ClickType.LEFT);

        // The click reaches the input step and opens a prompt; nothing teleports until the line is submitted.
        assertThat(prompt.prompts).isEqualTo(1);
        verify(usePlayerWarp, never()).useFor(eq(viewer), eq(warp.name()), any());

        prompt.submit("s3cret");

        // The submitted line rides %input% into the teleport action as the entered password.
        verify(usePlayerWarp).useFor(viewer, warp.name(), Optional.of("s3cret"));
    }

    @Test
    void clickingFavouriteReachesFavouritePlayerWarp() {
        PlayerWarp warp = warp(otherOwner, WarpAccess.PUBLIC);
        openView(warp);

        fireClick(13, ClickType.LEFT); // the add-favourite button (viewer has not starred it)

        verify(favouritePlayerWarp).favourite(viewer, warp.name());
    }

    @Test
    void clickingAStarReachesRatePlayerWarpWithThatManyStars() {
        PlayerWarp warp = warp(otherOwner, WarpAccess.PUBLIC);
        openView(warp);

        fireClick(15, ClickType.LEFT); // rate → opens the pwarp-rate menu carrying the same warp subject
        Inventory rate = top();
        assertThat(rate.getItem(11).getType()).isEqualTo(Material.NETHER_STAR); // the five star buttons

        fireClick(14, ClickType.LEFT); // the four-star button

        verify(ratePlayerWarp).rate(viewer, warp.name(), 4);
    }

    @Test
    void theRateButtonIsHiddenForTheWarpOwner() {
        Inventory inv = openView(warp(viewer, WarpAccess.PUBLIC)); // the viewer owns this warp

        // Owner-can't-rate: the rate cell is not drawn, so the backdrop shows through its slot instead.
        assertThat(inv.getItem(15).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void theManageButtonShowsForTheWarpOwner() {
        Inventory inv = openView(warp(viewer, WarpAccess.PUBLIC)); // the viewer owns this warp

        assertThat(inv.getItem(16).getType()).isEqualTo(Material.COMPARATOR);
    }

    @Test
    void theManageButtonIsHiddenForAStranger() {
        // The viewer neither owns nor holds a role on this warp (members.roleOf is stubbed empty in setUp).
        Inventory inv = openView(warp(otherOwner, WarpAccess.PUBLIC));

        assertThat(inv.getItem(16).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void theManageButtonShowsForAManagerAndOpensTheManagePanel() {
        when(members.roleOf(any(), any())).thenReturn(Optional.of(WarpRole.MANAGER));
        PlayerWarp warp = warp(otherOwner, WarpAccess.PUBLIC); // owned by another, but the viewer is a manager on it
        Inventory inv = openView(warp);
        assertThat(inv.getItem(16).getType()).isEqualTo(Material.COMPARATOR);

        fireClick(16, ClickType.LEFT); // the manage button opens the pwarp-manage panel for this warp

        assertThat(managed).containsExactly(warp.name());
    }

    /** Stub the warp read, wire a real engine with the recording prompt, then open the detail panel and return its window. */
    private Inventory openView(PlayerWarp warp) {
        when(repository.findByName(warp.name())).thenReturn(Optional.of(warp));
        wireEngine();
        menu.open(viewer, warp.name());
        return top();
    }

    private void wireEngine() {
        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        prompt = new RecordingPrompt();
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                System::currentTimeMillis,
                new PagedListSourceRegistry(),
                prompt);
        server.getPluginManager().registerEvents(listener, plugin);
        PlayerWarpNotifier notifier = new PlayerWarpNotifier(new KeyMessages(), noopSink());
        menu = new PlayerWarpViewMenu(
                menus,
                scheduler,
                repository,
                favourites,
                members,
                usePlayerWarp,
                favouritePlayerWarp,
                ratePlayerWarp,
                new KeyMessages(),
                notifier,
                (bukkitPlayer, ref) -> {},
                (ref, name) -> managed.add(name));
        menu.register(bindings, dataFolder, NOOP);
    }

    private PlayerWarp warp(PlayerRef owner, WarpAccess access) {
        return PlayerWarp.create(owner, owner.name(), PlayerWarpName.of("base"), at(), CLOCK.instant())
                .withId(PlayerWarpId.of(1))
                .withAccess(access, CLOCK.instant());
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
            if (key.key().equals("pwarp.gui.view.entry-name")) {
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
