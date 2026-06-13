package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeAdminCommand;
import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeCommand;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionsLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorView;
import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
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
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the homes Brigadier surface: {@code /home} opens the slot grid for the sender, and
 * {@code /homeadmin <player> del|tp|list} dispatches to the {@link HomeAdmin} use case against the target's set
 * by slot. The grid open is asserted by the menu the sender ends up viewing; the admin verbs by their effect on
 * the fake repository and the recording teleporter.
 */
class HomeCommandPathTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerMock target;
    private FakeHomeRepository repository;
    private RecordingTeleporter teleporter;
    private HomeServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        target = server.addPlayer("Bob");
        repository = new FakeHomeRepository();
        teleporter = new RecordingTeleporter();
        services = services();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void homeOpensTheSlotGrid() {
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home");

        Inventory open = player.getOpenInventory().getTopInventory();
        assertThat(open.getHolder()).isInstanceOf(SimpleGui.class);
    }

    @Test
    void homeAdminDeleteRemovesTheTargetSlot() {
        repository.put(home(targetRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "homeadmin Bob del 1"); // 1-based display number maps to slot index 0

        assertThat(repository.findSlot(targetRef(), HomeSlot.of(0))).isEmpty();
    }

    @Test
    void homeAdminTeleportDelegatesToTheTeleporter() {
        repository.put(home(targetRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "homeadmin Bob tp 1");

        assertThat(teleporter.hops).isEqualTo(1);
    }

    private PlayerRef targetRef() {
        return new PlayerRef(target.getUniqueId(), target.getName());
    }

    private CommandDispatcher<CommandSourceStack> register() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        Messages messages = new KeyMessages();
        dispatcher.getRoot().addChild(new HomeCommand(services, messages).build());
        dispatcher.getRoot().addChild(new HomeAdminCommand(services, messages).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private Home home(PlayerRef owner, int slot) {
        return Home.create(owner, HomeSlot.of(slot), Position.of(WORLD, slot, 64, slot), Instant.EPOCH);
    }

    private HomeServices services() {
        Messages messages = new KeyMessages();
        HomeNotifier notifier = new HomeNotifier(messages, new NoSink());
        DomainEventPublisher events = new NoEvents();
        Clock clock = Clock.system(ZoneOffset.UTC);
        HomeQuota quota = new HomeQuota(new AllowAllPermissions(), 3);
        Scheduler scheduler = new SyncScheduler();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
        IconSelectorView iconSelector = new IconSelectorView(
                messages,
                scheduler,
                new SetHomeIcon(repository, notifier, events, clock),
                IconSelectorLayout.codeDefault());
        HomeActionView actionView = new HomeActionView(
                messages,
                notifier,
                new AllowAllPermissions(),
                scheduler,
                new TeleportHome(repository, teleporter, notifier),
                new DeleteHome(repository, notifier, events),
                new RelocateHome(repository, List.of(), notifier, events, clock),
                new RenameHome(repository, notifier, events, clock),
                iconSelector,
                new AnvilInput(plugin),
                HomeActionsLayout.codeDefault(),
                fmt,
                false,
                false,
                false,
                false,
                pos -> false,
                new AlwaysAllowClaimService());
        HomeListView listView = new HomeListView(
                messages,
                notifier,
                new AllowAllPermissions(),
                scheduler,
                new ListHomes(repository),
                quota,
                new CreateHomeAtSlot(repository, quota, List.of(), notifier, events, 1000, clock),
                new SafeLocationGuard(server, false, false, 5),
                new AlwaysAllowClaimService(),
                actionView,
                HomeListLayout.codeDefault(),
                1000,
                fmt);
        HomeAdmin homeAdmin = new HomeAdmin(repository, teleporter, notifier, events);
        return new HomeServices(listView, actionView, iconSelector, homeAdmin, new ServerPlayerLookup(), repository);
    }

    /** A map-backed slot repository keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<Integer, Home>> byOwner = new ConcurrentHashMap<>();

        void put(Home home) {
            owned(home.owner()).put(home.slot().index(), home);
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, new ArrayList<>(owned(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return owned(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(owned(owner).get(slot.index()));
        }

        @Override
        public void save(Home home) {
            put(home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            owned(owner).remove(slot.index());
        }

        private Map<Integer, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), u -> new java.util.TreeMap<>());
        }
    }

    private static final class RecordingTeleporter implements HomeTeleporter {
        int hops;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            hops++;
        }
    }

    /** Resolves online players by name through the live mock server. */
    private final class ServerPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.ofNullable(server.getPlayerExact(name))
                    .map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(server.getPlayer(uuid)).map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return server.getPlayer(uuid) != null;
        }
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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
}
