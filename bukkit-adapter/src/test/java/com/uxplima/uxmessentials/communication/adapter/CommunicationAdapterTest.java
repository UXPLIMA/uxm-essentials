package com.uxplima.uxmessentials.communication.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.event.player.PlayerJoinEvent;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.communication.adapter.inbound.command.CommunicationCommands;
import com.uxplima.uxmessentials.communication.adapter.inbound.command.InfoPageCommand;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ConnectionMessageListener;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.AtomicSequenceCounter;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.adapter.outbound.PdcBroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the communication adapter against a real (mock) Bukkit server, end-to-end through the
 * split {@code modules/communication} content the {@link CommunicationContentCodec} parses: the
 * {@link ConnectionMessageListener} overriding a join message per a {@code CUSTOM} policy, an auto-registered info
 * command ({@code /rules}) printing the configured page through the real Brigadier node, and
 * {@code /broadcasttoggle} flipping the PDC-backed opt-out and confirming with the plugin's own
 * {@link CommunicationMessageKey}. The message sink records what each path delivered; the info commands are built
 * from the config-derived {@link InfoRegistry} exactly as the wiring builds them.
 */
class CommunicationAdapterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock player;
    private RecordingSink sink;
    private CommunicationSettings settings;
    private BroadcastOptOutStore optOutStore;

    @BeforeEach
    void setUp(@TempDir Path dataDir) throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        settings = new CommunicationSettings(writeContent(dataDir), new NoopLogger());
        // One store over one mock plugin so the toggle write and the assertion read the same PDC namespace.
        optOutStore = new PdcBroadcastOptOutStore(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theJoinListenerReplacesTheVanillaLineWithTheCustomTemplate() {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random());
        ConnectionMessageListener listener = new ConnectionMessageListener(
                new ResolveJoinMessage(engine, settings::joinPolicy),
                new ResolveQuitMessage(engine, settings::quitPolicy),
                settings);
        PlayerJoinEvent join = new PlayerJoinEvent(player, Component.text("Alice joined the game"));

        listener.onJoin(join);

        Component overridden = join.joinMessage();
        assertThat(overridden).isNotNull();
        assertThat(PLAIN.serialize(overridden)).isEqualTo("welcome Alice"); // {player} substituted, vanilla replaced
    }

    @Test
    void theAutoRegisteredRulesCommandPrintsTheConfiguredPage() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "rules");

        // The /rules command was auto-registered from the info-pages map and printed both operator lines verbatim.
        assertThat(sink.lines).containsExactly("Rule one", "Rule two");
    }

    @Test
    void aMultiPageInfoCommandDrawsTheHeaderAndFooterChromeAroundTheFirstPageSlice() {
        InfoPageCommand info = new InfoPageCommand(
                "info",
                InfoRegistry.of(List.of(InfoPage.of("info", List.of("a", "b", "c", "d", "e"), 2))),
                new BukkitInfoSender(sink),
                new CommunicationNotifier(sink, sink),
                sink);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(info.build());

        execute(dispatcher, "info");

        // Page one of a three-page body: the header is drawn first, then the first two body lines, then the footer.
        // The double resolves a MessageKey to its key string and delivers it, so the chrome appears in lines too —
        // assert the full interleaved order, which pins header → body slice → footer.
        assertThat(sink.lines)
                .containsExactly(
                        CommunicationMessageKey.INFO_PAGE_HEADER.key(),
                        "a",
                        "b",
                        CommunicationMessageKey.INFO_PAGE_FOOTER.key());
        assertThat(sink.keys)
                .containsExactly(CommunicationMessageKey.INFO_PAGE_HEADER, CommunicationMessageKey.INFO_PAGE_FOOTER);
    }

    @Test
    void theLastPageOfAnInfoCommandDrawsAHeaderButNoNextPageFooter() {
        InfoPageCommand info = new InfoPageCommand(
                "info",
                InfoRegistry.of(List.of(InfoPage.of("info", List.of("a", "b", "c", "d", "e"), 2))),
                new BukkitInfoSender(sink),
                new CommunicationNotifier(sink, sink),
                sink);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(info.build());

        execute(dispatcher, "info 3");

        // The final page shows the header but no "next page" footer; the slice is the single trailing line.
        assertThat(sink.lines).containsExactly(CommunicationMessageKey.INFO_PAGE_HEADER.key(), "e");
        assertThat(sink.keys).containsExactly(CommunicationMessageKey.INFO_PAGE_HEADER);
    }

    @Test
    void aSinglePageInfoCommandPrintsTheBodyWithNoPaginationChrome() {
        InfoPageCommand info = new InfoPageCommand(
                "info",
                InfoRegistry.of(List.of(InfoPage.of("info", List.of("one", "two"), 8))),
                new BukkitInfoSender(sink),
                new CommunicationNotifier(sink, sink),
                sink);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(info.build());

        execute(dispatcher, "info");

        // A body that fits in one page is printed verbatim with no header or footer keys.
        assertThat(sink.lines).containsExactly("one", "two");
        assertThat(sink.keys).isEmpty();
    }

    @Test
    void broadcastToggleFlipsTheOptOutBitAndConfirmsWithThePluginString() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();
        assertThat(optOutStore.receivesBroadcasts(ref())).isTrue(); // opted in by default

        execute(dispatcher, "broadcasttoggle"); // opt out

        assertThat(optOutStore.receivesBroadcasts(ref())).isFalse();
        assertThat(sink.keys).containsExactly(CommunicationMessageKey.BROADCAST_TOGGLE_OFF);
    }

    @Test
    void broadcastToggleTwiceOptsBackIn() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "broadcasttoggle"); // out
        execute(dispatcher, "broadcasttoggle"); // back in

        assertThat(optOutStore.receivesBroadcasts(ref())).isTrue();
        assertThat(sink.keys)
                .containsExactly(
                        CommunicationMessageKey.BROADCAST_TOGGLE_OFF, CommunicationMessageKey.BROADCAST_TOGGLE_ON);
    }

    @Test
    void theInfoRegistryAutoRegistersExactlyTheConfiguredPagesPlusBroadcastToggle() {
        List<String> literals = new ArrayList<>();
        for (CommandRegistration command : commands()) {
            literals.add(command.build().getLiteral());
        }
        assertThat(literals)
                .containsExactlyInAnyOrder(
                        "broadcast",
                        "broadcastworld",
                        "broadcasttoggle",
                        "announce",
                        "me",
                        "clearchat",
                        "togglechat",
                        "rules",
                        "motd");
    }

    @Test
    void broadcastIsListedInTheCommandSurface() {
        List<String> literals = new ArrayList<>();
        for (CommandRegistration command : commands()) {
            literals.add(command.build().getLiteral());
        }
        assertThat(literals).contains("broadcast");
    }

    @Test
    void broadcastDeliversTheMessageToOnlinePlayersAsOperatorContent() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "broadcast hello world");

        // The operator-authored body lands in the sink with the configured prefix prepended, never as a MessageKey.
        assertThat(sink.lines).hasSize(1);
        assertThat(sink.lines.get(0)).endsWith("hello world");
        assertThat(sink.keys).isEmpty();
    }

    @Test
    void meCommandBroadcastsActionToAllOnlinePlayers() {
        PlayerMock bob = server.addPlayer("Bob");
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "me waves");

        // The action lands on every online player through the parity-checked ME key, never as raw operator content.
        assertThat(sink.keys).containsExactly(CommunicationMessageKey.ME, CommunicationMessageKey.ME);
        assertThat(bob.getName()).isNotBlank();
    }

    private CommandDispatcher<CommandSourceStack> registerCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (CommandRegistration command : commands()) {
            dispatcher.getRoot().addChild(command.build());
        }
        return dispatcher;
    }

    private List<CommandRegistration> commands() {
        CommunicationNotifier notifier = new CommunicationNotifier(sink, sink);
        BroadcastOptOut optOut = new BroadcastOptOut(optOutStore, notifier, new NoEvents(), Clock.systemUTC());
        InfoRegistry registry = settings.infoRegistry();
        BukkitAnnouncerBroadcaster broadcaster = announcerBroadcaster();
        Scheduler scheduler = new SyncScheduler();
        AnnouncerTask announcer = new AnnouncerTask(
                scheduler,
                new NextAnnouncement(() -> settings.announcerConfig().rotating(), random()),
                broadcaster,
                settings::announcerConfig,
                () -> true);
        return CommunicationCommands.all(
                optOut,
                registry,
                new BukkitInfoSender(sink),
                notifier,
                sink,
                broadcaster,
                announcer,
                scheduler,
                sink,
                new com.uxplima.uxmessentials.communication.adapter.ChatLock(),
                settings);
    }

    private BukkitAnnouncerBroadcaster announcerBroadcaster() {
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), settings.announcerDisplay());
        return new BukkitAnnouncerBroadcaster(sink, optOutStore, channels, this::conditionContext, new SyncScheduler());
    }

    private ConditionContext conditionContext(org.bukkit.entity.Player who) {
        return new ConditionContext(
                who::hasPermission,
                who.getWorld().getName(),
                who.getGameMode().name(),
                java.util.function.UnaryOperator.identity());
    }

    private com.uxplima.uxmessentials.communication.application.port.RandomSource random() {
        return new ThreadLocalRandomSource();
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private Path writeContent(Path dataDir) throws Exception {
        Path dir = dataDir.resolve("modules").resolve("communication");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("join-quit.conf"), """
                join { mode = CUSTOM, ordering = SEQUENTIAL, templates = [ "welcome {player}" ] }
                quit { mode = DEFAULT }
                death { mode = DEFAULT }
                """);
        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { interval-seconds = 60, min-players = 0, ordering = SEQUENTIAL, lines = [] }\n");
        Files.writeString(dir.resolve("info-pages.conf"), """
                info-pages {
                  rules = [ "Rule one", "Rule two" ]
                  motd = [ "Welcome {player}" ]
                }
                """);
        return dir;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /**
     * Doubles as both ports: {@link Messages#resolve} records the {@link MessageKey} (and echoes its key as the
     * resolved string) so a delivered plugin string is observable, and {@link MessageSink#deliver} records the
     * raw operator line (info-page body, announcer line). The plugin's own confirmations therefore land in
     * {@code keys} while operator content lands in {@code lines}.
     */
    private static final class RecordingSink implements MessageSink, Messages {
        private final List<String> lines = new ArrayList<>();
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            lines.add(renderedText);
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key);
            return key.key();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event bridging is not under test here
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Runs every hop inline so the entity-thread and async-after work executes within the test. */
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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}
