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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.command.KitsCommand;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
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
import com.uxplima.uxmessentials.kits.domain.KitItem;
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
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /kits} browse menu through the real Brigadier {@code /kits} node and uxmLib's
 * {@code PaginatedGui}. Bare {@code /kits} opens a paginated menu whose content slots hold one display icon per
 * kit the player may claim — backed by a fake {@link KitRepository} of three kits — proving the read-only menu
 * renders one icon per available kit. {@code /kits list} keeps the chat path, asserted by the {@code KIT_LIST}
 * keys it produces. The scheduler is a synchronous double so the entity-bound open runs inline, and uxmLib's
 * menu listener is installed against a mock plugin (reset on teardown) exactly as the vault GUI test does.
 */
class KitsMenuPathTest {

    private static final int KIT_COUNT = 3;

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
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall(); // reset the static install state so the next test re-installs the menu listener
        MockBukkit.unmock();
    }

    @Test
    void bareKitsOpensAPaginatedMenuWithOneIconPerAvailableKit() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                registerCommand(com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.GUI);

        execute(dispatcher, "kits");

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(menu.getSize()).isEqualTo(54); // a 6-row paginated menu
        assertThat(contentIcons(menu)).isEqualTo(KIT_COUNT);
    }

    @Test
    void kitsListDrivesTheChatPath() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                registerCommand(com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.GUI);

        execute(dispatcher, "kits list");

        assertThat(sink.keys).contains(KitsMessageKey.KIT_LIST_HEADER);
    }

    @Test
    void bareKitsInChatModeListsInChatAndOpensNoInventory() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                registerCommand(com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.CHAT);

        execute(dispatcher, "kits");

        assertThat(sink.keys).contains(KitsMessageKey.KIT_LIST_HEADER);
        // Chat mode opens no inventory at all, so the player has no top inventory to hold a menu.
        assertThat(player.getOpenInventory().getTopInventory()).isNull();
    }

    /** Non-air icons in the content rows (slots 0..44), excluding the reserved bottom-row nav buttons. */
    private int contentIcons(Inventory menu) {
        int count = 0;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = menu.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    private CommandDispatcher<CommandSourceStack> registerCommand(
            com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode mode) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new KitsCommand(services, new KeyMessages(), () -> mode).build());
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
        KitGranter granter = (who, kit) -> KitGranter.Grant.complete();
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        Clock clock = Clock.systemUTC();
        ClaimKit claimKit =
                new ClaimKit(repository, access, granter, notifier, new NoEvents(), clock, Optional.empty());
        KitPreviewView kitPreview = new KitPreviewView(
                messages, new SyncScheduler(), GuiLayout.paginatedDefault(Material.GRAY_STAINED_GLASS_PANE));
        KitMenuView kitMenu = new KitMenuView(
                messages,
                notifier,
                new SyncScheduler(),
                claimKit,
                new StubKitCategoryRepository(),
                access,
                kitPreview,
                GuiLayout.paginatedDefault(Material.CHEST));
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

    /** Three free, repeatable, ungated, empty-item kits (the CHEST fallback icon dodges the item codec). */
    private static final class FakeRepository implements KitRepository {
        private final List<KitDefinition> kits = List.of(
                KitDefinition.repeatable(KitId.of("starter"), List.<KitItem>of(), Duration.ofSeconds(60)),
                KitDefinition.repeatable(KitId.of("daily"), List.<KitItem>of(), Duration.ofSeconds(120)),
                KitDefinition.repeatable(KitId.of("vip"), List.<KitItem>of(), Duration.ofSeconds(30)));

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
