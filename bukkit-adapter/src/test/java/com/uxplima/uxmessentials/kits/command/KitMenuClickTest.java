package com.uxplima.uxmessentials.kits.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.command.KitsCommand;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
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
 * MockBukkit coverage that a {@code /kits} menu icon click claims that kit through the same {@link ClaimKit}
 * use case the {@code /kit} command drives. The bare {@code /kits} node opens the paginated menu, then a
 * left-click on content slot 0 (the first kit, {@code starter}) is fired through the installed uxmLib menu
 * listener. Because the real {@link ClaimKit} sends {@code KIT_CLAIMED} for the granted kit, the test asserts
 * the click claimed exactly the clicked kit: the recording granter saw one grant and {@code KIT_CLAIMED}
 * carried {@code kit=starter}. A control click on an empty slot claims nothing.
 */
class KitMenuClickTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private KitServices services;
    private RecordingSink sink;
    private RecordingGranter granter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        granter = new RecordingGranter();
        services = services();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall(); // reset the static install state so the next test re-installs the menu listener
        MockBukkit.unmock();
    }

    @Test
    void clickingAKitIconClaimsThatKit() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        execute(dispatcher, "kits");
        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);

        fireClick(0);

        assertThat(granter.grants).hasSize(1);
        assertThat(sink.deliveries).anySatisfy(delivery -> {
            assertThat(delivery.key()).isEqualTo(KitsMessageKey.KIT_CLAIMED);
            assertThat(delivery.placeholders()).containsEntry("kit", "starter");
        });
    }

    @Test
    void clickingAnEmptySlotClaimsNothing() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        execute(dispatcher, "kits");

        fireClick(44); // a content row slot past the three kits, so nothing is bound there

        assertThat(granter.grants).isEmpty();
        assertThat(sink.keys).doesNotContain(KitsMessageKey.KIT_CLAIMED);
    }

    /** Left-click the given content slot of the open menu through the installed listener. */
    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private CommandDispatcher<CommandSourceStack> registerCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new KitsCommand(services, new KeyMessages()).build());
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
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        Clock clock = Clock.systemUTC();
        ClaimKit claimKit = new ClaimKit(repository, access, granter, notifier, new NoEvents(), clock);
        KitMenuView kitMenu = new KitMenuView(messages, new SyncScheduler(), claimKit);
        return new KitServices(
                claimKit,
                new ListKits(repository, permissions, claims, notifier),
                new ShowKit(repository, notifier),
                new CreateKit(repository, notifier),
                new DelKit(repository, notifier),
                new KitEditor(repository, notifier),
                new KitReset(repository, claims, notifier),
                kitMenu,
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

    /** Records every grant so the test can prove a click drove exactly one claim. */
    private static final class RecordingGranter implements KitGranter {
        private final List<List<KitItem>> grants = new ArrayList<>();

        @Override
        public Grant grant(PlayerRef recipient, List<KitItem> items) {
            grants.add(items);
            return Grant.complete();
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

    /** A resolved message: its key and the placeholders it carried, so a claim's target kit is assertable. */
    private record Delivery(MessageKey key, Map<String, String> placeholders) {}

    /** Records each delivered key so a path's outcome is asserted by the message it produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();
        private final List<Delivery> deliveries = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the recorded keys/deliveries drive asserts
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            sink.deliveries.add(new Delivery(key, Map.copyOf(placeholders)));
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
