package com.uxplima.uxmessentials.discordlink.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * MockBukkit coverage of the Discord link-status panel. The panel renders the viewer's binding status FRESH each
 * open (linked vs not), and surfaces only what the use cases support: a generate-code button (the {@link BeginLink}
 * use case the {@code /discordlink} command runs, told via the same chat messages) when not linked, and a
 * confirm-gated unlink (the {@link Unlink} use case the {@code /discordunlink} command runs) when linked. The
 * scheduler runs every hop inline so the off-thread unlink lands synchronously.
 */
class DiscordStatusViewTest {

    private static final int STATUS_SLOT = 11;
    private static final int ACTION_SLOT = 15;
    private static final int CONFIRM_SLOT = 11; // engine confirm-menu yes button (ConfirmRenderer.YES_SLOT)
    private static final int CANCEL_SLOT = 15; // engine confirm-menu no button (ConfirmRenderer.NO_SLOT)

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private InMemoryStore store;
    private BeginLink beginLink;
    private Unlink unlink;
    private LinkStatus linkStatus;
    private Notifier notifier;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        store = new InMemoryStore();
        Clock clock = Clock.systemUTC();
        beginLink = new BeginLink(store, clock, new Random(1), Duration.ofMinutes(10));
        unlink = new Unlink(store, event -> {});
        linkStatus = new LinkStatus(store);
        sink = new RecordingSink();
        notifier = new Notifier(new KeyMessages(), sink);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersTheLinkButtonWhenNotLinked() throws Exception {
        view().open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(STATUS_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD); // status line
        assertThat(inv.getItem(ACTION_SLOT).getType()).isEqualTo(Material.NAME_TAG); // generate-code action
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back / close
    }

    @Test
    void rendersTheUnlinkButtonWhenLinked() throws Exception {
        store.confirm(new ConfirmedLink(viewer.uuid(), DiscordId.of("123456789012345678"), Instant.now()));
        view().open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(ACTION_SLOT).getType()).isEqualTo(Material.BARRIER); // unlink action
    }

    @Test
    void linkButtonGeneratesACodeThroughTheUseCase() throws Exception {
        view().open(player, viewer);
        assertThat(store.pending).isEmpty();

        fireClick(ACTION_SLOT, ClickType.LEFT); // runs BeginLink and tells the player the code via chat

        assertThat(store.pending).containsKey(viewer.uuid()); // a fresh pending code was persisted
        assertThat(sink.deliveries).isNotEmpty(); // the code + how-to chat messages were sent
    }

    @Test
    void linkButtonDeclinesAndIssuesNoCodeWhenTheBridgeIsAbsent() throws Exception {
        view(false).open(player, viewer);
        assertThat(store.pending).isEmpty();

        fireClick(ACTION_SLOT, ClickType.LEFT); // with no connected bridge a code redeems nowhere

        assertThat(store.pending).isEmpty(); // nothing was persisted
        assertThat(sink.deliveries).containsExactly("discordlink.not-configured"); // the decline message only
    }

    @Test
    void unlinkIsConfirmGatedAndOnlyUnlinksOnConfirm() throws Exception {
        store.confirm(new ConfirmedLink(viewer.uuid(), DiscordId.of("123456789012345678"), Instant.now()));
        view().open(player, viewer);

        fireClick(ACTION_SLOT, ClickType.LEFT); // opens the confirm menu, unbinds nothing
        assertThat(store.findByPlayer(viewer.uuid())).isPresent();

        fireClick(CANCEL_SLOT, ClickType.LEFT); // cancel reopens the panel, still linked
        assertThat(store.findByPlayer(viewer.uuid())).isPresent();

        fireClick(ACTION_SLOT, ClickType.LEFT); // reopen the confirm menu
        fireClick(CONFIRM_SLOT, ClickType.LEFT); // confirm runs Unlink

        assertThat(store.findByPlayer(viewer.uuid())).isEmpty(); // the binding was removed
    }

    private DiscordStatusView view() throws Exception {
        return view(true);
    }

    private DiscordStatusView view(boolean bridgeAvailable) throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new DiscordStatusView(
                guiText,
                scheduler,
                layouts,
                new KeyMessages(),
                beginLink,
                unlink,
                linkStatus,
                notifier,
                () -> bridgeAvailable,
                engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel and its confirm open through the runtime. */
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
        Path file = dir.resolve("modules").resolve("discordlink").resolve("gui").resolve("discord-status.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [11, 15]
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

    /** A simple in-memory link store standing in for the DB-backed jOOQ store. */
    private static final class InMemoryStore implements DiscordLinkStore {
        private final Map<UUID, PendingLink> pending = new HashMap<>();
        private final Map<UUID, ConfirmedLink> confirmed = new HashMap<>();

        @Override
        public void savePending(PendingLink p) {
            pending.put(p.player(), p);
        }

        @Override
        public Optional<PendingLink> findPendingByCode(LinkCode code) {
            return pending.values().stream().filter(p -> p.code().equals(code)).findFirst();
        }

        @Override
        public void deletePending(UUID player) {
            pending.remove(player);
        }

        @Override
        public void confirm(ConfirmedLink link) {
            confirmed.put(link.player(), link);
            pending.remove(link.player());
        }

        @Override
        public Optional<ConfirmedLink> findByPlayer(UUID player) {
            return Optional.ofNullable(confirmed.get(player));
        }

        @Override
        public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
            return confirmed.values().stream()
                    .filter(c -> c.discordId().equals(discordId))
                    .findFirst();
        }

        @Override
        public boolean unlink(UUID player) {
            return confirmed.remove(player) != null;
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> deliveries = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            deliveries.add(renderedText);
        }
    }

    /** Echoes the key and any placeholder values so a rendered value reveals the substituted state. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (placeholders.isEmpty()) {
                return key.key();
            }
            return key.key() + " " + String.join(" ", placeholders.values());
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
