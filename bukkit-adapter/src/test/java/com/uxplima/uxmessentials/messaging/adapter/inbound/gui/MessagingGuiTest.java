package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the messaging settings panel. The accept-messages toggle renders at its conf slot and a
 * click flips and persists it; the social-spy toggle is present only for a permitted viewer. The panel is laid out
 * from a temp conf (no hardcoded slots) and the scheduler runs every hop inline so the off-thread reads and writes
 * land synchronously. The ignore-list manager and the mailbox, now engine-rendered, are covered by
 * {@code IgnoreMenuGoldenTest} and {@code MailboxMenuGoldenTest}.
 */
class MessagingGuiTest {

    private static final List<Integer> SETTINGS_SLOTS = List.of(11, 15);
    private static final int ACCEPT_SLOT = SETTINGS_SLOTS.get(0);
    private static final int SOCIALSPY_SLOT = SETTINGS_SLOTS.get(1);

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeToggles toggles;
    private FakeSocialSpy socialSpy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        toggles = new FakeToggles();
        socialSpy = new FakeSocialSpy();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void settingsAcceptToggleRendersAtItsSlotAndAClickFlipsAndPersists() throws Exception {
        MessagingSettingsView view = settingsView(dir, new GrantingPermissions(false));
        view.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(ACCEPT_SLOT).getType()).isEqualTo(Material.WRITABLE_BOOK);
        assertThat(toggles.accepts).isTrue();

        fireClick(ACCEPT_SLOT, ClickType.LEFT);

        assertThat(toggles.accepts).isFalse();
    }

    @Test
    void socialSpyToggleIsPresentForStaffAndAbsentForAPlayer() throws Exception {
        MessagingSettingsView staffView = settingsView(dir, new GrantingPermissions(true));
        assertThat(staffView.panel().settingAt(SOCIALSPY_SLOT, viewer)).isPresent();

        MessagingSettingsView playerView = settingsView(dir, new GrantingPermissions(false));
        assertThat(playerView.panel().settingAt(SOCIALSPY_SLOT, viewer)).isEmpty();
    }

    // --- view builders ---

    private MessagingSettingsView settingsView(Path dir, Permissions permissions) throws Exception {
        settingsLayout(dir);
        return new MessagingSettingsView(
                guiText, scheduler, new GuiLayouts(dir, NOOP), new KeyMessages(), toggles, socialSpy, permissions);
    }

    private void settingsLayout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("messaging").resolve("gui").resolve("messaging-settings.conf");
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

    // --- helpers ---

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    // --- fakes ---

    private static final class FakeToggles implements MessageToggleStore {
        private boolean accepts = true;

        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return accepts;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            accepts = !accepts;
            return accepts;
        }
    }

    private static final class FakeSocialSpy implements SocialSpyStore {
        private boolean spying = false;

        @Override
        public boolean isSpying(PlayerRef who) {
            return spying;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            spying = !spying;
            return spying;
        }

        @Override
        public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
            return false;
        }

        @Override
        public List<PlayerRef> activeSpies() {
            return List.of();
        }

        @Override
        public java.util.Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
            return new java.util.HashSet<>();
        }
    }

    private static final class GrantingPermissions implements Permissions {
        private final boolean grant;

        GrantingPermissions(boolean grant) {
            this.grant = grant;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grant;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
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
