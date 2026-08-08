package com.uxplima.uxmessentials.kits.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.command.KitCommand;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitBrowseMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Folia threading coverage of {@code /kit <name> <player>} (the staff give). The grant mutates the
 * <em>recipient's</em> inventory through the {@link KitGranter}, so it must run on the recipient's region thread,
 * never the giving sender's. This drives the real Brigadier give node and asserts, via a scheduler that records
 * every {@code onEntity} hop, that the grant is dispatched onto the recipient and not the sender.
 *
 * <p>The self-claim path ({@code /kit <name>}) stays on the player's own thread and is unaffected; the give path
 * is the one that crosses regions when sender and recipient differ.
 */
class KitGiveDispatchTest {

    private ServerMock server;
    private PlayerMock sender;
    private PlayerMock recipient;
    private RecordingScheduler scheduler;
    private GrantRecorder granter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sender = server.addPlayer("Alice");
        sender.setOp(true);
        recipient = server.addPlayer("Bob");
        scheduler = new RecordingScheduler();
        granter = new GrantRecorder();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void givingAKitToAnotherPlayerDispatchesTheGrantOntoTheRecipientsThread() {
        execute("kit starter Bob");

        PlayerRef recipientRef = new PlayerRef(recipient.getUniqueId(), "Bob");
        PlayerRef senderRef = new PlayerRef(sender.getUniqueId(), "Alice");
        assertThat(scheduler.entityHops).contains(recipientRef);
        assertThat(scheduler.entityHops).doesNotContain(senderRef);
        assertThat(granter.grantedTo).containsExactly(recipientRef);
    }

    private void execute(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(new KitCommand(
                                services(),
                                new KeyMessages(),
                                () -> ListDisplayMode.GUI,
                                () -> ListDisplayMode.GUI,
                                scheduler)
                        .build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private KitServices services() {
        Messages messages = new KeyMessages();
        Permissions permissions = new AllowAllPermissions();
        KitClaimStore claims = new NoClaims();
        Notifier notifier = new Notifier(messages, new RecordingSink());
        KitRepository repository = new FakeRepository();
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        Clock clock = Clock.systemUTC();
        ClaimKit claimKit =
                new ClaimKit(repository, access, granter, notifier, new NoEvents(), clock, Optional.empty());
        KitPreviewView kitPreview = new KitPreviewView(
                messages, new SyncScheduler(), GuiLayout.paginatedDefault(Material.GRAY_STAINED_GLASS_PANE));
        // The /kit give dispatch path never opens the browse menu, so the engine-backed browse view is built here
        // only to satisfy KitServices; it is never registered or opened in this test.
        KitBrowseMenu kitMenu = new KitBrowseMenu(
                com.uxplima.uxmessentials.shared.menu.TestMenuEngine.create(messages, new SyncScheduler())
                        .menus(),
                new SyncScheduler(),
                claimKit,
                notifier,
                new StubKitCategoryRepository(),
                access,
                kitPreview,
                messages,
                GuiLayout.paginatedDefault(Material.CHEST),
                Clock.systemUTC());
        KitEditor kitEditor = new KitEditor(repository, notifier);
        KitEditorView kitEditorView = new KitEditorView(messages, kitEditor, new SyncScheduler());
        return new KitServices(
                claimKit,
                new ListKits(repository, permissions, claims, notifier),
                new ShowKit(repository, notifier),
                new CreateKit(repository, notifier),
                new DelKit(repository, notifier),
                kitEditor,
                new KitReset(repository, claims, notifier),
                kitMenu,
                kitPreview,
                kitEditorView,
                null,
                new ServerPlayerLookup(),
                null,
                null,
                null,
                null);
    }

    /** A free, repeatable, ungated kit named {@code starter} with no items (so the grant is a pure recorded hop). */
    private static final class FakeRepository implements KitRepository {
        private final List<KitDefinition> kits =
                List.of(KitDefinition.repeatable(KitId.of("starter"), List.<KitItem>of(), Duration.ZERO));

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return kits.stream().filter(kit -> kit.id().equals(id)).findFirst();
        }

        @Override
        public List<KitDefinition> all() {
            return kits;
        }

        @Override
        public boolean exists(KitId id) {
            return find(id).isPresent();
        }

        @Override
        public void save(KitDefinition definition) {}

        @Override
        public void delete(KitId id) {}
    }

    /** Records the recipient of every grant so the test can assert who the kit was handed to. */
    private static final class GrantRecorder implements KitGranter {
        private final List<PlayerRef> grantedTo = new ArrayList<>();

        @Override
        public KitGranter.Grant grant(PlayerRef recipient, KitDefinition kit) {
            grantedTo.add(recipient);
            return KitGranter.Grant.complete();
        }
    }

    /** Runs every task inline (so the path completes synchronously) but records each {@code onEntity} target. */
    private static final class RecordingScheduler implements Scheduler {
        private final List<PlayerRef> entityHops = new ArrayList<>();

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
            entityHops.add(player);
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

    private static final class NoClaims implements KitClaimStore {
        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return false;
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {}

        @Override
        public void reset(PlayerRef who, KitId kit) {}

        @Override
        public void resetAll(PlayerRef who) {}
    }

    private static final class RecordingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private final class ServerPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            var bukkit = server.getPlayerExact(name);
            return bukkit == null ? Optional.empty() : Optional.of(new PlayerRef(bukkit.getUniqueId(), name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            var bukkit = server.getPlayer(uuid);
            return bukkit != null && bukkit.isOnline();
        }
    }
}
