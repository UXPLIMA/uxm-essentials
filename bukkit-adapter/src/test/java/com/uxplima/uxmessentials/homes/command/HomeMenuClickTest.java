package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomesCommand;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenuView;
import com.uxplima.uxmessentials.homes.application.DelHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.MoveHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHome;
import com.uxplima.uxmessentials.homes.application.SetMainHome;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * MockBukkit coverage that a {@code /homes} menu icon click teleports to that home through the same
 * {@link TeleportHome} use case the {@code /home <name>} command drives. The bare {@code /homes} node opens the
 * paginated menu, then a left-click on content slot 0 (the first home, {@code base}) is fired through the
 * installed uxmLib menu listener. Because the real {@link TeleportHome} delegates the hop to the teleport context
 * through the {@link HomeTeleporter} and sends {@code HOME_TELEPORTING} for the chosen home, the test asserts the
 * click teleported exactly the clicked home: the recording teleporter saw one hop and {@code HOME_TELEPORTING}
 * carried {@code home=base}. A control click on an empty slot teleports nothing.
 */
class HomeMenuClickTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private HomeServices services;
    private RecordingSink sink;
    private RecordingTeleporter teleporter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        teleporter = new RecordingTeleporter();
        services = services();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall(); // reset the static install state so the next test re-installs the menu listener
        MockBukkit.unmock();
    }

    @Test
    void clickingAHomeIconTeleportsToThatHome() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        execute(dispatcher, "homes");
        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);

        fireClick(0);

        assertThat(teleporter.hops).hasSize(1);
        assertThat(teleporter.hops.get(0).name().value()).isEqualTo("base");
        assertThat(sink.deliveries).anySatisfy(delivery -> {
            assertThat(delivery.key()).isEqualTo(HomesMessageKey.HOME_TELEPORTING);
            assertThat(delivery.placeholders()).containsEntry("home", "base");
        });
    }

    @Test
    void clickingAnEmptySlotTeleportsNothing() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        execute(dispatcher, "homes");

        fireClick(44); // a content row slot past the three homes, so nothing is bound there

        assertThat(teleporter.hops).isEmpty();
        assertThat(sink.keys).doesNotContain(HomesMessageKey.HOME_TELEPORTING);
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
        dispatcher.getRoot().addChild(new HomesCommand(services, new KeyMessages()).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private HomeServices services() {
        Messages messages = new KeyMessages();
        HomeNotifier notifier = new HomeNotifier(messages, sink);
        HomeRepository repository = new FakeHomeRepository();
        MainHomePreference mainHomes = new NoMainHome();
        HomeQuota quota = new HomeQuota(new AllowAllPermissions(), 3);
        Clock clock = Clock.systemUTC();
        TeleportHome teleportHome = new TeleportHome(repository, mainHomes, teleporter, notifier);
        HomeMenuView homeMenu = new HomeMenuView(
                messages, new SyncScheduler(), teleportHome, GuiLayout.paginatedDefault(Material.RED_BED));
        return new HomeServices(
                new SetHome(repository, quota, notifier, new NoEvents(), clock),
                new DelHome(repository, notifier, new NoEvents()),
                new ListHomes(repository, notifier),
                teleportHome,
                new RenameHome(repository, notifier),
                new MoveHome(repository, notifier),
                new SetMainHome(repository, mainHomes, notifier),
                new HomeAdmin(repository, teleporter, notifier, new NoEvents()),
                homeMenu,
                new NoPlayerLookup(),
                repository);
    }

    /** A main-home store that holds no choice, so plain {@code /home} keeps using the first-created home. */
    private static final class NoMainHome implements MainHomePreference {
        @Override
        public Optional<HomeName> mainHomeOf(PlayerRef owner) {
            return Optional.empty();
        }

        @Override
        public void setMainHome(PlayerRef owner, HomeName name) {}

        @Override
        public void clear(PlayerRef owner) {}
    }

    /** Three owner-owned homes in creation order, returned for any owner the menu/list asks for. */
    private static final class FakeHomeRepository implements HomeRepository {
        private final List<Home> homes = homes();

        private static List<Home> homes() {
            WorldRef world = new WorldRef(UUID.randomUUID(), "world");
            PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
            Instant now = Instant.now();
            return List.of(
                    Home.create(owner, HomeName.of("base"), Position.of(world, 0, 64, 0), now),
                    Home.create(owner, HomeName.of("mine"), Position.of(world, 10, 64, 10), now),
                    Home.create(owner, HomeName.of("farm"), Position.of(world, 20, 64, 20), now));
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, homes);
        }

        @Override
        public int count(PlayerRef owner) {
            return homes.size();
        }

        @Override
        public void save(Home home) {}

        @Override
        public void rename(PlayerRef owner, HomeName from, HomeName to) {}

        @Override
        public void delete(PlayerRef owner, HomeName name) {}
    }

    /** Records every hop so the test can prove a click drove exactly one teleport for the bound home. */
    private static final class RecordingTeleporter implements HomeTeleporter {
        private final List<Home> hops = new ArrayList<>();

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            hops.add(home);
        }
    }

    /** A resolved message: its key and the placeholders it carried, so a hop's target home is assertable. */
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

    private static final class NoEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
    }
}
