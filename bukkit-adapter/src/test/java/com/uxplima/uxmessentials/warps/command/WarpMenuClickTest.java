package com.uxplima.uxmessentials.warps.command;

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
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpsCommand;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpMenuView;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpInfo;
import com.uxplima.uxmessentials.warps.application.WarpNotifier;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
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
 * MockBukkit coverage that a {@code /warps} menu icon click warps to that warp through the same {@link UseWarp}
 * use case the {@code /warp} command drives. The bare {@code /warps} node opens the paginated menu, then a
 * left-click on content slot 0 (the first warp, {@code spawn}) is fired through the installed uxmLib menu
 * listener. Because the real {@link UseWarp} delegates the hop to the teleport context through the
 * {@link WarpTeleporter} and sends {@code WARP_TELEPORTING} for the chosen warp, the test asserts the click
 * warped exactly the clicked warp: the recording teleporter saw one hop and {@code WARP_TELEPORTING} carried
 * {@code warp=spawn}. A control click on an empty slot warps nothing.
 */
class WarpMenuClickTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private WarpServices services;
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
    void clickingAWarpIconTeleportsToThatWarp() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        execute(dispatcher, "warps");
        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);

        fireClick(0);

        assertThat(teleporter.hops).hasSize(1);
        assertThat(teleporter.hops.get(0).name().value()).isEqualTo("spawn");
        assertThat(sink.deliveries).anySatisfy(delivery -> {
            assertThat(delivery.key()).isEqualTo(WarpsMessageKey.WARP_TELEPORTING);
            assertThat(delivery.placeholders()).containsEntry("warp", "spawn");
        });
    }

    @Test
    void clickingAnEmptySlotTeleportsNothing() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        execute(dispatcher, "warps");

        fireClick(44); // a content row slot past the three warps, so nothing is bound there

        assertThat(teleporter.hops).isEmpty();
        assertThat(sink.keys).doesNotContain(WarpsMessageKey.WARP_TELEPORTING);
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
        dispatcher
                .getRoot()
                .addChild(new WarpsCommand(
                                services,
                                new KeyMessages(),
                                () -> com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.GUI)
                        .build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private WarpServices services() {
        Messages messages = new KeyMessages();
        Permissions permissions = new AllowAllPermissions();
        WarpNotifier notifier = new WarpNotifier(messages, sink);
        WarpRepository repository = new FakeRepository();
        WarpAccess access = new WarpAccess(permissions, Optional.<WarpEconomy>empty());
        Clock clock = Clock.systemUTC();
        UseWarp useWarp = new UseWarp(repository, access, teleporter, notifier, pos -> true, permissions);
        WarpMenuView warpMenu = new WarpMenuView(
                messages, new SyncScheduler(), useWarp, GuiLayout.paginatedDefault(Material.ENDER_PEARL));
        return new WarpServices(
                useWarp,
                new SetWarp(repository, notifier, new NoEvents(), clock, List.of()),
                new DelWarp(repository, notifier, new NoEvents()),
                new ListWarps(repository, permissions, notifier),
                new WarpInfo(repository, notifier),
                new MoveWarp(repository, notifier),
                warpMenu,
                new NoPlayerLookup(),
                repository,
                null);
    }

    /** Three free, ungated, owner-attributed warps. */
    private static final class FakeRepository implements WarpRepository {
        private final List<Warp> warps = warps();

        private static List<Warp> warps() {
            WorldRef world = new WorldRef(UUID.randomUUID(), "world");
            PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
            Instant now = Instant.now();
            return List.of(
                    Warp.create(WarpName.of("spawn"), Position.of(world, 0, 64, 0), owner, now),
                    Warp.create(WarpName.of("shop"), Position.of(world, 10, 64, 10), owner, now),
                    Warp.create(WarpName.of("pvp"), Position.of(world, 20, 64, 20), owner, now));
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return warps.stream().filter(warp -> warp.name().equals(name)).findFirst();
        }

        @Override
        public List<Warp> all() {
            return warps;
        }

        @Override
        public boolean exists(WarpName name) {
            return find(name).isPresent();
        }

        @Override
        public void save(Warp warp) {}

        @Override
        public void delete(WarpName name) {}

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    /** Records every hop so the test can prove a click drove exactly one teleport for the bound warp. */
    private static final class RecordingTeleporter implements WarpTeleporter {
        private final List<Warp> hops = new ArrayList<>();

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            hops.add(warp);
        }
    }

    /** A resolved message: its key and the placeholders it carried, so a hop's target warp is assertable. */
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
