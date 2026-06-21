package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.MessagingNotifier;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the three messaging GUIs. The settings panel renders the accept-messages toggle at its
 * conf slot and a click flips and persists it; the social-spy toggle is present only for a permitted viewer. The
 * ignore-list manager renders the viewer's ignored players, a click un-ignores through the use case, and the add
 * seam ignores a resolvable name. The mailbox renders the viewer's mail, a click opens the read-only detail, and
 * the clear button is confirm-gated. The views are laid out from temp confs (no hardcoded slots) and the
 * scheduler runs every hop inline so the off-thread reads and writes land synchronously.
 */
class MessagingGuiTest {

    private static final List<Integer> SETTINGS_SLOTS = List.of(11, 15);
    private static final int ACCEPT_SLOT = SETTINGS_SLOTS.get(0);
    private static final int SOCIALSPY_SLOT = SETTINGS_SLOTS.get(1);
    private static final List<Integer> CONTENT_SLOTS = List.of(10, 11);
    private static final int CREATE_SLOT = 49;
    private static final int CONFIRM_SLOT = 11; // uxmLib ConfirmMenu's confirm button slot
    private static final int CANCEL_SLOT = 15; // uxmLib ConfirmMenu's cancel button slot
    private static final int DETAIL_BACK_SLOT = 22;

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
    private FakeIgnores ignores;
    private FakeMail mail;
    private Ignore ignore;
    private Unignore unignore;
    private ClearMail clearMail;
    private AnvilInput anvil;

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
        ignores = new FakeIgnores();
        mail = new FakeMail();
        MessagingNotifier notifier = new MessagingNotifier(new KeyMessages(), new NoopSink());
        ignore = new Ignore(ignores, notifier);
        unignore = new Unignore(ignores, notifier);
        clearMail = new ClearMail(mail, notifier);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        anvil.uninstall();
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

    @Test
    void ignoreListRendersTheViewersIgnoredPlayers() throws Exception {
        ignores.ignore(viewer, ref("Mallory"), IgnoreScope.ALL);
        ignores.ignore(viewer, ref("Eve"), IgnoreScope.ALL);
        IgnoreListView view = ignoreView(dir);

        view.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(CONTENT_SLOTS.get(0)).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(CONTENT_SLOTS.get(1)).getType()).isEqualTo(Material.PLAYER_HEAD);
    }

    @Test
    void clickingAnIgnoreEntryUnignoresThroughTheUseCase() throws Exception {
        ignores.ignore(viewer, ref("Mallory"), IgnoreScope.ALL);
        IgnoreListView view = ignoreView(dir);
        view.open(player, viewer);
        assertThat(ignores.load(viewer).ignores(ref("Mallory"))).isTrue();

        fireClick(CONTENT_SLOTS.get(0), ClickType.LEFT);

        assertThat(ignores.load(viewer).ignores(ref("Mallory"))).isFalse();
    }

    @Test
    void addingAResolvableNameIgnoresThroughTheUseCase() throws Exception {
        IgnoreListView view = ignoreView(dir);
        view.open(player, viewer);
        assertThat(ignores.load(viewer).size()).isZero();

        view.addByName(player, "Mallory");

        assertThat(ignores.load(viewer).ignores(ref("Mallory"))).isTrue();
    }

    @Test
    void mailboxRendersMailAndAClickOpensTheDetail() throws Exception {
        mail.deliver(viewer, "Bob", "hello there");
        MailboxView view = mailboxView(dir);

        view.open(player, viewer);
        Inventory list = player.getOpenInventory().getTopInventory();
        assertThat(list.getItem(CONTENT_SLOTS.get(0)).getType()).isEqualTo(Material.WRITTEN_BOOK);

        fireClick(CONTENT_SLOTS.get(0), ClickType.LEFT);

        Inventory detail = player.getOpenInventory().getTopInventory();
        // The detail sub-menu replaces the screen: the back button sits at its fixed slot.
        assertThat(detail.getItem(DETAIL_BACK_SLOT).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clearButtonIsConfirmGatedAndOnlyClearsOnConfirm() throws Exception {
        mail.deliver(viewer, "Bob", "hello there");
        MailboxView view = mailboxView(dir);
        view.open(player, viewer);

        fireClick(CREATE_SLOT, ClickType.LEFT); // opens the confirm menu, does not clear
        assertThat(mail.load(viewer).isEmpty()).isFalse();

        fireClick(CANCEL_SLOT, ClickType.LEFT); // cancel reopens the mailbox, box untouched
        assertThat(mail.load(viewer).isEmpty()).isFalse();

        fireClick(CREATE_SLOT, ClickType.LEFT); // reopen the confirm menu
        fireClick(CONFIRM_SLOT, ClickType.LEFT); // confirm empties the box
        assertThat(mail.load(viewer).isEmpty()).isTrue();
    }

    // --- view builders ---

    private MessagingSettingsView settingsView(Path dir, Permissions permissions) throws Exception {
        settingsLayout(dir);
        return new MessagingSettingsView(
                guiText, scheduler, new GuiLayouts(dir, NOOP), new KeyMessages(), toggles, socialSpy, permissions);
    }

    private IgnoreListView ignoreView(Path dir) throws Exception {
        EntityListLayout layout = listLayout(dir, "ignore-list");
        return new IgnoreListView(guiText, scheduler, ignores, ignore, unignore, new OnlineLookup(), anvil, layout);
    }

    private MailboxView mailboxView(Path dir) throws Exception {
        EntityListLayout layout = listLayout(dir, "mailbox");
        return new MailboxView(guiText, scheduler, mail, clearMail, layout);
    }

    // --- layout confs ---

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

    private EntityListLayout listLayout(Path dir, String name) throws Exception {
        Path file = dir.resolve("modules").resolve("messaging").resolve("gui").resolve(name + ".conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 6
                fallback-icon = "PLAYER_HEAD"
                nav-icon = "ARROW"
                filler = "BLACK_STAINED_GLASS_PANE"
                prev-slot = 48
                next-slot = 50
                create-slot = 49
                create-icon = "LIME_DYE"
                content-slots = [10, 11]
                """);
        return new GuiLayouts(dir, NOOP)
                .loadEntityList(
                        "messaging", name, EntityListLayout.withCreate(Material.PLAYER_HEAD, 49, Material.LIME_DYE));
    }

    // --- helpers ---

    private static PlayerRef ref(String name) {
        return new PlayerRef(uuid(name), name);
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
        public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
            return new HashSet<>();
        }
    }

    private static final class FakeIgnores implements IgnoreStore {
        private final Map<UUID, Map<UUID, PlayerRef>> store = new HashMap<>();

        @Override
        public IgnoreList load(PlayerRef owner) {
            Map<UUID, PlayerRef> entries = store.getOrDefault(owner.uuid(), Map.of());
            List<com.uxplima.uxmessentials.messaging.domain.IgnoreEntry> rows = new ArrayList<>();
            for (PlayerRef ignored : entries.values()) {
                rows.add(com.uxplima.uxmessentials.messaging.domain.IgnoreEntry.all(ignored));
            }
            return IgnoreList.of(owner, rows);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            store.computeIfAbsent(owner.uuid(), k -> new LinkedHashMap<>()).put(ignored.uuid(), ignored);
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            Map<UUID, PlayerRef> entries = store.get(owner.uuid());
            if (entries != null) {
                entries.remove(ignored.uuid());
            }
        }
    }

    private static final class FakeMail implements MailRepository {
        private final Map<UUID, List<MailItem>> boxes = new HashMap<>();
        private long nextId = 1L;

        void deliver(PlayerRef recipient, String sender, String body) {
            MailItem item = new MailItem(
                    MailId.of(nextId++),
                    recipient,
                    MailSender.system(sender),
                    MessageBody.of(body),
                    Instant.now(),
                    false);
            boxes.computeIfAbsent(recipient.uuid(), k -> new ArrayList<>()).add(item);
        }

        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.of(recipient, boxes.getOrDefault(recipient.uuid(), List.of()));
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return load(recipient).unreadCount();
        }

        @Override
        public MailItem append(MailItem item) {
            return item;
        }

        @Override
        public void markAllRead(PlayerRef recipient) {
            List<MailItem> items = boxes.get(recipient.uuid());
            if (items == null) {
                return;
            }
            items.replaceAll(MailItem::markRead);
        }

        @Override
        public void clear(PlayerRef recipient) {
            boxes.remove(recipient.uuid());
        }

        @Override
        public int deleteSentBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class OnlineLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.of(ref(name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(new PlayerRef(uuid, "player"));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
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

    private static final class NoopSink implements MessageSink {
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
