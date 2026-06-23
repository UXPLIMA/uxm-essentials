package com.uxplima.uxmessentials.communication.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
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
 * MockBukkit coverage of the communication admin panel. The chat-lock button flips and persists the live
 * {@link ChatLock} the {@code /togglechat} command flips; the clearchat button is confirm-gated and only fans out a
 * screenful of blank lines on confirm; the broadcast seam sends through the same {@link BukkitAnnouncerBroadcaster}
 * as {@code /broadcast}; and the read-only announcer list renders one entry per configured announcement. The
 * scheduler runs every hop inline so the off-thread setter and fan-out land synchronously.
 */
class CommunicationAdminViewTest {

    private static final List<Integer> BUTTON_SLOTS = List.of(10, 12, 14, 16);
    private static final int LOCK_SLOT = BUTTON_SLOTS.get(0);
    private static final int CLEARCHAT_SLOT = BUTTON_SLOTS.get(1);
    private static final int ANNOUNCER_SLOT = BUTTON_SLOTS.get(3);
    private static final List<Integer> ANNOUNCER_CONTENT = List.of(10, 11);
    private static final int CONFIRM_SLOT = 11; // uxmLib ConfirmMenu confirm button
    private static final int CANCEL_SLOT = 15; // uxmLib ConfirmMenu cancel button

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private ChatLock chatLock;
    private RecordingSink sink;
    private BukkitAnnouncerBroadcaster broadcaster;
    private CommunicationNotifier notifier;
    private TextInput textInput;
    private final List<Announcement> announcements = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        chatLock = new ChatLock();
        sink = new RecordingSink();
        notifier = new CommunicationNotifier(new KeyMessages(), sink);
        broadcaster = new BukkitAnnouncerBroadcaster(
                sink, new AlwaysReceives(), new ChannelBroadcaster(scheduler, display()), p -> null, scheduler);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, java.nio.file.Path.of("nonexistent"), NOOP);
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void chatLockToggleFlipsAndPersists() throws Exception {
        CommunicationAdminView view = view();
        view.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(LOCK_SLOT).getType()).isEqualTo(Material.IRON_DOOR);
        assertThat(chatLock.isLocked()).isFalse();

        fireClick(LOCK_SLOT, ClickType.LEFT);

        assertThat(chatLock.isLocked()).isTrue(); // flipped through the live ChatLock the command uses
    }

    @Test
    void clearchatButtonIsConfirmGatedAndOnlyFlushesOnConfirm() throws Exception {
        CommunicationAdminView view = view();
        view.open(player, viewer);

        fireClick(CLEARCHAT_SLOT, ClickType.LEFT); // opens the confirm menu, flushes nothing
        assertThat(sink.deliveries).isEmpty();

        fireClick(CANCEL_SLOT, ClickType.LEFT); // cancel reopens the panel, still nothing flushed
        assertThat(sink.deliveries).isEmpty();

        fireClick(CLEARCHAT_SLOT, ClickType.LEFT); // reopen the confirm menu
        fireClick(CONFIRM_SLOT, ClickType.LEFT); // confirm runs the /clearchat fan-out

        assertThat(sink.blankLines()).isGreaterThanOrEqualTo(100); // a screenful of blank lines was pushed
    }

    @Test
    void broadcastSeamSendsThroughTheBroadcaster() throws Exception {
        CommunicationAdminView view = view();

        view.submitBroadcast("server restarting"); // the anvil-callback seam

        // The broadcaster fans the prefixed line out to every online, opted-in player through the sink.
        assertThat(sink.deliveries).anyMatch(line -> line.endsWith("server restarting"));
    }

    @Test
    void blankBroadcastInputIsANoOp() throws Exception {
        CommunicationAdminView view = view();

        view.submitBroadcast("   ");

        assertThat(sink.deliveries).isEmpty();
    }

    @Test
    void announcerListRendersOneEntryPerAnnouncement() throws Exception {
        announcements.add(announcement("welcome"));
        announcements.add(announcement("rules"));
        CommunicationAdminView view = view();
        view.open(player, viewer);

        fireClick(ANNOUNCER_SLOT, ClickType.LEFT); // opens the read-only announcer list

        Inventory list = player.getOpenInventory().getTopInventory();
        assertThat(list.getItem(ANNOUNCER_CONTENT.get(0)).getType()).isEqualTo(Material.PAPER);
        assertThat(list.getItem(ANNOUNCER_CONTENT.get(1)).getType()).isEqualTo(Material.PAPER);
    }

    private CommunicationAdminView view() throws Exception {
        panelLayout();
        announcerLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new CommunicationAdminView(
                guiText,
                scheduler,
                layouts,
                new KeyMessages(),
                chatLock,
                broadcaster,
                "<prefix> ",
                () -> new AnnouncerConfig(Duration.ofMinutes(5), 0, Ordering.SEQUENTIAL, List.copyOf(announcements)),
                notifier,
                sink,
                textInput);
    }

    private void panelLayout() throws Exception {
        Path file =
                dir.resolve("modules").resolve("communication").resolve("gui").resolve("communication-admin.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [10, 12, 14, 16]
                back-slot = 22
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private void announcerLayout() throws Exception {
        Path file =
                dir.resolve("modules").resolve("communication").resolve("gui").resolve("announcer-list.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 6
                fallback-icon = "PAPER"
                nav-icon = "ARROW"
                filler = "BLACK_STAINED_GLASS_PANE"
                prev-slot = 48
                next-slot = 50
                create-slot = -1
                create-icon = "LIME_DYE"
                content-slots = [10, 11]
                """);
    }

    private static Announcement announcement(String id) {
        return new Announcement(
                id,
                List.of("<body>line</body>"),
                DisplayCondition.always(),
                java.util.Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                java.util.Optional.empty(),
                false);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> deliveries = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            deliveries.add(renderedText);
        }

        long blankLines() {
            return deliveries.stream().filter(String::isEmpty).count();
        }
    }

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    private static final class AlwaysReceives implements BroadcastOptOutStore {
        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }

        @Override
        public void forget(PlayerRef who) {}
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
