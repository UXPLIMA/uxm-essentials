package com.uxplima.uxmessentials.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeLimitReachedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeRenameEvent;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeEventBridges;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.homes.domain.event.HomeLimitReached;
import com.uxplima.uxmessentials.homes.domain.event.HomeRenamed;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.BukkitEventBridge;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The bridge from a published domain fact to the Bukkit event other plugins listen to.
 *
 * <p>Three properties matter and each has a case here. A bridged fact reaches its event carrying the fact's values;
 * an unbridged fact is silently ignored rather than throwing, because plenty of facts are deliberately internal; and
 * with nobody listening the mapper never runs at all. That last one is the load-bearing one: domain facts are
 * published on hot paths, so the cost of the bridge on a server with no consumer plugin has to be a lookup, not an
 * allocation.
 */
class BukkitEventBridgeTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position SOMEWHERE = new Position(new WorldRef(UUID.randomUUID(), "world"), 1, 64, 2, 0f, 0f);

    private ServerMock server;
    private Plugin plugin;
    private CountingScheduler scheduler;
    private BukkitEventBridge bridge;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new CountingScheduler();
        EventBridgeRegistry registry = new EventBridgeRegistry();
        EventBridges.installAll(registry);
        bridge = new BukkitEventBridge(registry, scheduler, server.getPluginManager(), new SilentLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBridgedFactBecomesItsEventCarryingTheFactsValues() {
        List<UxmHomeCreateEvent> seen = new ArrayList<>();
        listenFor(UxmHomeCreateEvent.class, seen::add);

        bridge.accept(new HomeCreated(OWNER, HomeSlot.of(2), SOMEWHERE));

        assertThat(seen).hasSize(1);
        UxmHomeCreateEvent event = seen.get(0);
        assertThat(event.getPlayerId()).isEqualTo(OWNER.uuid());
        assertThat(event.getPlayerName()).isEqualTo("Steve");
        assertThat(event.getSlot()).isEqualTo(2);
        assertThat(event.getSlotNumber())
                .as("the number the player sees counts from one")
                .isEqualTo(3);
        assertThat(event.getLocation().world()).isEqualTo("world");
        assertThat(event.getLocation().y()).isEqualTo(64);
    }

    @Test
    void everyHomeFactReachesItsOwnEventType() {
        List<Class<?>> seen = new ArrayList<>();
        listenFor(UxmHomeCreateEvent.class, event -> seen.add(event.getClass()));
        listenFor(UxmHomeDeleteEvent.class, event -> seen.add(event.getClass()));
        listenFor(UxmHomeRenameEvent.class, event -> seen.add(event.getClass()));
        listenFor(UxmHomeLimitReachedEvent.class, event -> seen.add(event.getClass()));

        bridge.accept(new HomeCreated(OWNER, HomeSlot.of(0), SOMEWHERE));
        bridge.accept(new HomeDeleted(OWNER, HomeSlot.of(0)));
        bridge.accept(new HomeRenamed(OWNER, HomeSlot.of(0)));
        bridge.accept(new HomeLimitReached(OWNER, 3, 3));

        assertThat(seen)
                .containsExactly(
                        UxmHomeCreateEvent.class,
                        UxmHomeDeleteEvent.class,
                        UxmHomeRenameEvent.class,
                        UxmHomeLimitReachedEvent.class);
    }

    @Test
    void aLimitFactCarriesTheCountAndTheCap() {
        List<UxmHomeLimitReachedEvent> seen = new ArrayList<>();
        listenFor(UxmHomeLimitReachedEvent.class, seen::add);

        bridge.accept(new HomeLimitReached(OWNER, 5, 5));

        assertThat(seen.get(0).getCurrentCount()).isEqualTo(5);
        assertThat(seen.get(0).getLimit()).isEqualTo(5);
    }

    @Test
    void withNoListenerNothingIsBuiltAndNothingIsScheduled() {
        bridge.accept(new HomeCreated(OWNER, HomeSlot.of(0), SOMEWHERE));

        assertThat(scheduler.scheduled)
                .as("with nobody listening the bridge must not even reach the scheduler")
                .hasValue(0);
    }

    @Test
    void anEventIsDeliveredOnItsSubjectsOwnRegion() {
        listenFor(UxmHomeCreateEvent.class, event -> {});

        bridge.accept(new HomeCreated(OWNER, HomeSlot.of(0), SOMEWHERE));

        assertThat(scheduler.entityHops)
                .as("a home is about one player, so its event goes to that player's region")
                .hasValue(1);
        assertThat(scheduler.globalHops).hasValue(0);
    }

    @Test
    void anUnbridgedFactIsIgnoredRatherThanThrowing() {
        // Plenty of facts stay internal on purpose. Reaching the bridge with one must be a no-op, because the
        // alternative is that adding an internal domain event breaks every publish that carries it.
        EventBridgeRegistry empty = new EventBridgeRegistry();
        HomeEventBridges.register(empty);

        new BukkitEventBridge(empty, scheduler, server.getPluginManager(), new SilentLogger())
                .accept(new InternalOnlyFact());

        assertThat(scheduler.scheduled).hasValue(0);
    }

    /** Registers a listener for one event type without needing a class per case. */
    private <T extends Event> void listenFor(Class<T> type, Consumer<T> sink) {
        server.getPluginManager()
                .registerEvent(
                        type,
                        new Listener() {},
                        EventPriority.NORMAL,
                        (listener, event) -> {
                            if (type.isInstance(event)) {
                                sink.accept(type.cast(event));
                            }
                        },
                        plugin);
    }

    /** A fact no bridge maps, standing in for every deliberately internal domain event. */
    private record InternalOnlyFact() implements DomainEvent {}

    /** Runs tasks inline and counts which region each one asked for. */
    private static final class CountingScheduler implements Scheduler {
        private final AtomicInteger scheduled = new AtomicInteger();
        private final AtomicInteger entityHops = new AtomicInteger();
        private final AtomicInteger globalHops = new AtomicInteger();

        @Override
        public void onGlobal(Runnable task) {
            scheduled.incrementAndGet();
            globalHops.incrementAndGet();
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            scheduled.incrementAndGet();
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            scheduled.incrementAndGet();
            entityHops.incrementAndGet();
            task.run();
        }

        @Override
        public void async(Runnable task) {
            scheduled.incrementAndGet();
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            scheduled.incrementAndGet();
            task.run();
        }
    }

    /** The bridge only logs when a mapper fails, which no case here provokes. */
    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
