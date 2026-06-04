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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.command.ShowKitCommand;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitNotifier;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
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
 * MockBukkit coverage of the {@code /showkit} GUI preview path through the real Brigadier {@code /showkit} node.
 * In {@code gui} mode the command opens a read-only managed menu sized to fit the kit, with the kit's stacks laid
 * out at their definition-order slots and every interaction cancelled by {@link KitPreviewListener}, so a player
 * can inspect a kit's contents without taking anything. In {@code chat} mode the command opens no inventory and
 * sends the chat preview lines instead. The scheduler is a synchronous double so the entity-bound open runs
 * inline, mirroring the {@code /kits} menu path test.
 */
class ShowKitGuiPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private KitServices services;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        services = services();
        server.getPluginManager().registerEvents(new KitPreviewListener(), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void guiModeOpensAReadOnlyMenuHoldingTheKitItems() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(ListDisplayMode.GUI);

        execute(dispatcher, "showkit starter");

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu).isNotNull();
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(menu.getItem(1)).isNotNull();
        assertThat(menu.getItem(1).getType()).isEqualTo(Material.GOLDEN_APPLE);
    }

    @Test
    void everyClickInTheGuiPreviewIsCancelled() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(ListDisplayMode.GUI);
        execute(dispatcher, "showkit starter");
        InventoryView view = player.getOpenInventory();

        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);

        assertThat(click.isCancelled()).isTrue();
    }

    @Test
    void chatModeSendsTheChatLinesAndOpensNothing() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(ListDisplayMode.CHAT);

        execute(dispatcher, "showkit starter");

        assertThat(sink.keys).contains(KitsMessageKey.KIT_PREVIEW_HEADER);
        assertThat(sink.keys).contains(KitsMessageKey.KIT_PREVIEW_ENTRY);
        assertThat(player.getOpenInventory().getTopInventory()).isNull();
    }

    private CommandDispatcher<CommandSourceStack> registerCommand(ListDisplayMode mode) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new ShowKitCommand(services, new KeyMessages(), () -> mode).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private KitServices services() {
        Messages messages = new KeyMessages();
        Permissions permissions = new AllowAllPermissions();
        KitClaimStore claims = new NoClaims();
        KitNotifier notifier = new KitNotifier(messages, sink);
        KitRepository repository = new FakeRepository();
        KitGranter granter = (who, items) -> KitGranter.Grant.complete();
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        Clock clock = Clock.systemUTC();
        ClaimKit claimKit = new ClaimKit(repository, access, granter, notifier, new NoEvents(), clock);
        KitMenuView kitMenu =
                new KitMenuView(messages, new SyncScheduler(), claimKit, GuiLayout.paginatedDefault(Material.CHEST));
        KitPreviewView kitPreview = new KitPreviewView(messages, new SyncScheduler());
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
                new NoPlayerLookup());
    }

    /** A single kit named {@code starter} holding two real, codec-encoded stacks at slots 0 and 1. */
    private static final class FakeRepository implements KitRepository {
        private final KitDefinition starter = KitDefinition.repeatable(
                KitId.of("starter"),
                List.of(
                        KitItemCodec.encode(new ItemStack(Material.DIAMOND_SWORD)),
                        KitItemCodec.encode(new ItemStack(Material.GOLDEN_APPLE, 3))),
                Duration.ofSeconds(60));

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return id.equals(starter.id()) ? Optional.of(starter) : Optional.empty();
        }

        @Override
        public List<KitDefinition> all() {
            return List.of(starter);
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

    /** Records each delivered key so a path's outcome is asserted by the message it produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the key list is what tests assert on
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
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

    private static final class NoCooldowns implements com.uxplima.uxmessentials.shared.application.port.Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }
}
