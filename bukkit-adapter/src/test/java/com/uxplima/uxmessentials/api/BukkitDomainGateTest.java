package com.uxplima.uxmessentials.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreRelocateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.kit.UxmKitPreClaimEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpPreCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpPreDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmPlayerPreTeleportEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpPreCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpPreDeleteEvent;
import com.uxplima.uxmessentials.api.view.UxmTeleportKind;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreating;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleting;
import com.uxplima.uxmessentials.homes.domain.event.HomeRelocating;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.event.KitClaiming;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpCreating;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleting;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.BukkitDomainGate;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleporting;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.event.WarpCreating;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The gate that puts a domain proposal to the rest of the server as a cancellable Bukkit event.
 *
 * <p>What has to hold: a cancelled event refuses the action, an uncancelled one allows it, and a proposal nothing
 * listens for is allowed without building anything. And the failure direction is the load-bearing one: a listener
 * that throws must not take the player's action down with it, because the alternative is one broken third-party
 * plugin making {@code /sethome} impossible.
 */
class BukkitDomainGateTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position SOMEWHERE = new Position(new WorldRef(UUID.randomUUID(), "world"), 1, 64, 2, 0f, 0f);

    private ServerMock server;
    private Plugin plugin;
    private CountingLogger log;
    private DomainGate gate;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        log = new CountingLogger();
        VetoRegistry registry = new VetoRegistry();
        EventBridges.installAllVetoes(registry);
        gate = new BukkitDomainGate(registry, server.getPluginManager(), log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCancelledPreEventRefusesTheAction() {
        listenFor(UxmHomePreCreateEvent.class, event -> event.setCancelled(true));

        assertThat(gate.allows(new HomeCreating(OWNER, HomeSlot.of(0), SOMEWHERE)))
                .isFalse();
    }

    @Test
    void aListenerThatDoesNotCancelAllowsTheAction() {
        List<UxmHomePreCreateEvent> seen = new ArrayList<>();
        listenFor(UxmHomePreCreateEvent.class, seen::add);

        assertThat(gate.allows(new HomeCreating(OWNER, HomeSlot.of(4), SOMEWHERE)))
                .isTrue();
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getPlayerId()).isEqualTo(OWNER.uuid());
        assertThat(seen.get(0).getSlot()).isEqualTo(4);
        assertThat(seen.get(0).getLocation().world()).isEqualTo("world");
    }

    @Test
    void eachVetoableHomeOperationAsksItsOwnEvent() {
        List<Class<?>> asked = new ArrayList<>();
        listenFor(UxmHomePreCreateEvent.class, event -> asked.add(event.getClass()));
        listenFor(UxmHomePreDeleteEvent.class, event -> asked.add(event.getClass()));
        listenFor(UxmHomePreRelocateEvent.class, event -> asked.add(event.getClass()));

        gate.allows(new HomeCreating(OWNER, HomeSlot.of(0), SOMEWHERE));
        gate.allows(new HomeDeleting(OWNER, HomeSlot.of(0)));
        gate.allows(new HomeRelocating(OWNER, HomeSlot.of(0), SOMEWHERE));

        assertThat(asked)
                .containsExactly(
                        UxmHomePreCreateEvent.class, UxmHomePreDeleteEvent.class, UxmHomePreRelocateEvent.class);
    }

    @Test
    void everyVetoableOperationInEveryContextReachesItsOwnEvent() {
        // One test rather than one per context: what matters is that the mapping exists and is not crossed with a
        // neighbour's, which is exactly the mistake a hand-written registry invites.
        List<Class<?>> asked = new ArrayList<>();
        Consumer<UxmCancellableEvent> record = event -> asked.add(event.getClass());
        listenFor(UxmPlayerPreTeleportEvent.class, record::accept);
        listenFor(UxmWarpPreCreateEvent.class, record::accept);
        listenFor(UxmWarpPreDeleteEvent.class, record::accept);
        listenFor(UxmPlayerWarpPreCreateEvent.class, record::accept);
        listenFor(UxmPlayerWarpPreDeleteEvent.class, record::accept);
        listenFor(UxmKitPreClaimEvent.class, record::accept);

        gate.allows(new PlayerTeleporting(OWNER, TeleportKind.HOME, SOMEWHERE));
        gate.allows(new WarpCreating(WarpName.of("shop"), OWNER, SOMEWHERE));
        gate.allows(new WarpDeleting(WarpName.of("shop"), OWNER));
        gate.allows(new PlayerWarpCreating(OWNER, PlayerWarpName.of("base"), SOMEWHERE));
        gate.allows(new PlayerWarpDeleting(OWNER, PlayerWarpName.of("base")));
        gate.allows(new KitClaiming(KitId.of("starter"), OWNER, OWNER));

        assertThat(asked)
                .containsExactly(
                        UxmPlayerPreTeleportEvent.class,
                        UxmWarpPreCreateEvent.class,
                        UxmWarpPreDeleteEvent.class,
                        UxmPlayerWarpPreCreateEvent.class,
                        UxmPlayerWarpPreDeleteEvent.class,
                        UxmKitPreClaimEvent.class);
    }

    @Test
    void aPreEventCarriesTheDetailAListenerNeedsToDecide() {
        List<UxmPlayerPreTeleportEvent> seen = new ArrayList<>();
        listenFor(UxmPlayerPreTeleportEvent.class, seen::add);

        gate.allows(new PlayerTeleporting(OWNER, TeleportKind.WARP, SOMEWHERE));

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).getKind()).isEqualTo(UxmTeleportKind.WARP);
        assertThat(seen.get(0).getDestination().world()).isEqualTo("world");
        assertThat(seen.get(0).getPlayerId()).isEqualTo(OWNER.uuid());
    }

    @Test
    void withNoListenerTheProposalIsAllowedWithoutBuildingAnEvent() {
        // The ordinary case on the ordinary server, and the reason a veto point is affordable on a hot path: no
        // listener means no event object, so the cost of asking is a map lookup and an array-length read.
        assertThat(HandlerList.getRegisteredListeners(plugin)).isEmpty();

        assertThat(gate.allows(new HomeCreating(OWNER, HomeSlot.of(0), SOMEWHERE)))
                .isTrue();
    }

    @Test
    void aProposalNothingCanRefuseIsAllowed() {
        assertThat(gate.allows(new NotVetoable())).isTrue();
    }

    @Test
    void aListenerThatThrowsIsLoggedAndTheActionProceeds() {
        listenFor(UxmHomePreCreateEvent.class, event -> {
            throw new IllegalStateException("the consumer plugin is broken");
        });

        assertThat(gate.allows(new HomeCreating(OWNER, HomeSlot.of(0), SOMEWHERE)))
                .as("one broken listener must not make the operation impossible")
                .isTrue();
        assertThat(log.errors).hasSize(1);
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

    /** A proposal no veto mapping covers, standing in for an operation the API deliberately does not open up. */
    private record NotVetoable() implements DomainProposal {}

    /** Counts the errors, which is the only level the gate ever writes at. */
    private static final class CountingLogger implements Logger {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
