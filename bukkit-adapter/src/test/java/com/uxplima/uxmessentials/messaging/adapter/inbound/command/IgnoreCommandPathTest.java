package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemorySocialSpyStore;
import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.ListIgnores;
import com.uxplima.uxmessentials.messaging.application.MessagingNotifier;
import com.uxplima.uxmessentials.messaging.application.MsgToggle;
import com.uxplima.uxmessentials.messaging.application.ReadMail;
import com.uxplima.uxmessentials.messaging.application.Reply;
import com.uxplima.uxmessentials.messaging.application.ReplyToggle;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMailToAll;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.SocialSpy;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /ignore} and {@code /ignorelist} after the offline-support and fold-into-GUI
 * rework. {@code /ignore <player>} now resolves offline-capably, so a player who has joined before can be ignored
 * while offline (the write lands on the UUID-keyed store), and only a never-seen name is rejected as unknown.
 * {@code /ignore} with no argument and the standalone {@code /ignorelist} both open the one ignore-list GUI, so
 * the list lives in a single place. The lookup is a fake that knows one offline profile; the GUI is the real view.
 */
class IgnoreCommandPathTest {

    private static final String PERMISSION = "uxmessentials.msg.ignore";
    private static final Instant T0 = Instant.parse("2026-06-14T12:00:00Z");

    private ServerMock server;
    private Plugin plugin;
    private RecordingSink sink;
    private RecordingIgnores ignores;
    private RecordingLookup players;
    private MessagingServices services;
    private MessagingGuiViews views;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        Guis.install(plugin);
        sink = new RecordingSink();
        ignores = new RecordingIgnores();
        players = new RecordingLookup(server);
        services = services();
        views = views(dir);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void ignoringAnOfflineButRealPlayerResolvesAndWrites() {
        PlayerMock owner = addPlayer("Owner");

        execute(new IgnoreCommand(services, new KeyMessages(), sink, views), owner, "ignore Ghost");

        // "Ghost" never joined this session but the offline lookup knows the profile, so the UUID-keyed write lands.
        assertThat(ignores.ignored).containsExactly("Ghost");
        assertThat(sink.keys).doesNotContain(SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
    }

    @Test
    void ignoringANeverSeenNameRepliesUnknownAndWritesNothing() {
        PlayerMock owner = addPlayer("Owner");

        execute(new IgnoreCommand(services, new KeyMessages(), sink, views), owner, "ignore Nobody");

        assertThat(ignores.ignored).isEmpty();
        assertThat(sink.keys).contains(SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
    }

    @Test
    void bareIgnoreOpensTheIgnoreListGui() {
        PlayerMock owner = addPlayer("Owner");

        execute(new IgnoreCommand(services, new KeyMessages(), sink, views), owner, "ignore");

        assertThat(openGuiHolder(owner)).isInstanceOf(PaginatedGui.class);
    }

    @Test
    void ignoreListOpensTheSameIgnoreListGui() {
        PlayerMock owner = addPlayer("Owner");

        execute(new IgnoreListCommand(services, new KeyMessages(), sink, views), owner, "ignorelist");

        assertThat(openGuiHolder(owner)).isInstanceOf(PaginatedGui.class);
    }

    private PlayerMock addPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        player.addAttachment(plugin, PERMISSION, true);
        return player;
    }

    private static Object openGuiHolder(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top.getHolder();
    }

    private void execute(CommandRegistration command, PlayerMock sender, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private MessagingServices services() {
        Messages messages = new KeyMessages();
        MessagingNotifier notifier = new MessagingNotifier(messages, sink);
        MessageDelivery delivery = new NoDelivery();
        MutePolicy mute = MutePolicy.NEVER;
        InMemorySocialSpyStore spies = new InMemorySocialSpyStore();
        ReplyRoutingStore replies = new AcceptingReplies();
        MailRepository mail = new EmptyMail();
        VanishVisibility vanish = (viewer, target) -> false;
        DomainEventPublisher events = new NoEvents();
        Scheduler scheduler = new SyncScheduler();
        var clock = java.time.Clock.fixed(T0, java.time.ZoneOffset.UTC);
        SendMessage send = new SendMessage(
                delivery,
                ignores,
                new com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore(),
                new AcceptingToggles(),
                spies,
                mute,
                new com.uxplima.uxmessentials.messaging.adapter.MutableAfkStatus(),
                mail,
                false,
                notifier,
                events,
                clock);
        return new MessagingServices(
                send,
                new Reply(
                        send,
                        new com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore(),
                        players,
                        vanish,
                        replies,
                        notifier,
                        Duration.ofMinutes(5),
                        clock),
                new SendMail(mail, ignores, delivery, mute, notifier, events, clock),
                new SendMailToAll(mail, clock),
                new ReadMail(mail, delivery, notifier),
                new ClearMail(mail, notifier),
                new MsgToggle(new AcceptingToggles(), notifier),
                new ReplyToggle(replies, notifier),
                new Ignore(ignores, notifier),
                new Unignore(ignores, notifier),
                new ListIgnores(ignores, notifier),
                new SocialSpy(spies, notifier),
                new HelpOp(
                        new com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience(),
                        delivery,
                        mute,
                        notifier,
                        events,
                        clock),
                players,
                vanish,
                scheduler);
    }

    private MessagingGuiViews views(Path dir) {
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return MessagingGuiViews.create(
                guiText,
                scheduler,
                new KeyMessages(),
                new UnlimitedPermissions(),
                services,
                new AcceptingToggles(),
                new InMemorySocialSpyStore(),
                ignores,
                new EmptyMail(),
                players,
                textInput,
                layouts);
    }

    /** Resolves online players against the live mock server; "Ghost" is the one known offline profile. */
    private static final class RecordingLookup implements PlayerLookup {
        private final ServerMock server;

        RecordingLookup(ServerMock server) {
            this.server = server;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            Player online = server.getPlayerExact(name);
            return online == null
                    ? Optional.empty()
                    : Optional.of(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            Optional<PlayerRef> online = findOnlineByName(name);
            if (online.isPresent()) {
                return online;
            }
            return name.equals("Ghost") ? Optional.of(new PlayerRef(UUID.randomUUID(), "Ghost")) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            Player online = server.getPlayer(uuid);
            return online == null
                    ? Optional.empty()
                    : Optional.of(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return server.getPlayer(uuid) != null;
        }
    }

    /** Records every ignore write by ignored-name so an offline write is observable; reads back as a live list. */
    private static final class RecordingIgnores implements IgnoreStore {
        private final List<String> ignored = new ArrayList<>();
        private final List<IgnoreEntry> entries = new ArrayList<>();

        @Override
        public IgnoreList load(PlayerRef owner) {
            return IgnoreList.of(owner, List.copyOf(entries));
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef target, IgnoreScope scope) {
            ignored.add(target.name());
            entries.add(new IgnoreEntry(target, scope));
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef target) {
            entries.removeIf(entry -> entry.ignored().equals(target));
        }
    }

    private static final class NoDelivery implements MessageDelivery {
        @Override
        public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void notifyNewMail(PlayerRef recipient, MailItem item) {}

        @Override
        public void deliverMailLine(PlayerRef reader, MailItem item) {}

        @Override
        public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {}
    }

    private static final class EmptyMail implements MailRepository {
        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.empty(recipient);
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return 0;
        }

        @Override
        public MailItem append(MailItem item) {
            return item.withId(MailId.of(1L));
        }

        @Override
        public void markAllRead(PlayerRef recipient) {}

        @Override
        public void clear(PlayerRef recipient) {}

        @Override
        public int deleteSentBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class AcceptingToggles implements MessageToggleStore {
        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class AcceptingReplies implements ReplyRoutingStore {
        @Override
        public boolean acceptsReplies(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    /** Records each resolved key so the unknown-player reply is observable, and echoes the key as its own text. */
    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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

    /** The GUI assembler reads node grants for its buttons; every node is granted so the list renders fully. */
    private static final class UnlimitedPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
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

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
