package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimRegion;
import me.angeschossen.lands.api.events.ChunkDeleteEvent;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimExpirationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Drives the two typed claim-deletion bridges and the port they feed. The GriefPrevention events are
 * constructed directly around a stubbed {@link Claim}; the Lands {@link ChunkDeleteEvent} is stubbed whole
 * (its only public constructor takes seven Lands-internal arguments). Calling each listener method directly
 * exercises the exact translation without standing up either plugin. A live MockBukkit server backs the
 * present-guard test, which proves an absent plugin leaves no listener and throws nothing.
 */
class ClaimDeletionEventsTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void griefPreventionDeletion_reachesSinkWithExactCornerBox() {
        List<ClaimRegion> captured = new ArrayList<>();
        GriefPreventionClaimDeletion bridge = new GriefPreventionClaimDeletion(captured::add, noOpLog());
        UUID worldId = UUID.randomUUID();

        bridge.onClaimDeleted(new ClaimDeletedEvent(claimWithCorners(worldId, 10, 20, 40, 55)));

        assertThat(captured).containsExactly(new ClaimRegion(new WorldRef(worldId, "world"), 10, 20, 40, 55));
    }

    @Test
    void griefPreventionExpiry_reachesSinkWithExactCornerBox() {
        List<ClaimRegion> captured = new ArrayList<>();
        GriefPreventionClaimDeletion bridge = new GriefPreventionClaimDeletion(captured::add, noOpLog());
        UUID worldId = UUID.randomUUID();

        bridge.onClaimExpired(new ClaimExpirationEvent(claimWithCorners(worldId, -8, -8, 7, 7)));

        assertThat(captured).containsExactly(new ClaimRegion(new WorldRef(worldId, "world"), -8, -8, 7, 7));
    }

    @Test
    void griefPreventionDeletion_swapsCornersGivenInAnyOrder() {
        // A provider handing the corners in the opposite order must still yield a valid (min<=max) box.
        List<ClaimRegion> captured = new ArrayList<>();
        GriefPreventionClaimDeletion bridge = new GriefPreventionClaimDeletion(captured::add, noOpLog());
        UUID worldId = UUID.randomUUID();

        bridge.onClaimDeleted(new ClaimDeletedEvent(claimWithCorners(worldId, 40, 55, 10, 20)));

        assertThat(captured).containsExactly(new ClaimRegion(new WorldRef(worldId, "world"), 10, 20, 40, 55));
    }

    @Test
    void landsChunkDeletion_reachesSinkWith16x16BoxAtChunkOffset() {
        List<ClaimRegion> captured = new ArrayList<>();
        LandsClaimDeletion bridge = new LandsClaimDeletion(captured::add, noOpLog());
        UUID worldId = UUID.randomUUID();
        World world = world(worldId);
        ChunkDeleteEvent event = mock(ChunkDeleteEvent.class);
        when(event.getWorld()).thenReturn(world);
        when(event.getX()).thenReturn(3);
        when(event.getZ()).thenReturn(-2);

        bridge.onChunkDeleted(event);

        // chunk (3, -2) -> block box [48..63] x [-32..-17]
        assertThat(captured).containsExactly(new ClaimRegion(new WorldRef(worldId, "world"), 48, -32, 63, -17));
    }

    @Test
    void port_fansEveryDeletionOutToAllRegisteredSinks() {
        BukkitClaimDeletionEvents events = new BukkitClaimDeletionEvents(noOpLog());
        List<ClaimRegion> first = new ArrayList<>();
        List<ClaimRegion> second = new ArrayList<>();
        events.onDeleted(first::add);
        events.onDeleted(second::add);
        ClaimRegion region = new ClaimRegion(new WorldRef(UUID.randomUUID(), "world"), 0, 0, 15, 15);

        events.emit(region);

        assertThat(first).containsExactly(region);
        assertThat(second).containsExactly(region);
    }

    @Test
    void register_installsNoListenerAndDoesNotThrow_whenNeitherPluginPresent() {
        BukkitClaimDeletionEvents events = new BukkitClaimDeletionEvents(noOpLog());

        assertThatCode(() -> events.register(plugin, server)).doesNotThrowAnyException();

        assertThat(bridgeListeners(ClaimDeletedEvent.getHandlerList())).isEmpty();
        assertThat(bridgeListeners(ClaimExpirationEvent.getHandlerList())).isEmpty();
        assertThat(bridgeListeners(ChunkDeleteEvent.getHandlerList())).isEmpty();
    }

    private static List<Listener> bridgeListeners(HandlerList handlers) {
        List<Listener> found = new ArrayList<>();
        for (RegisteredListener registered : handlers.getRegisteredListeners()) {
            Listener listener = registered.getListener();
            if (listener instanceof GriefPreventionClaimDeletion || listener instanceof LandsClaimDeletion) {
                found.add(listener);
            }
        }
        return found;
    }

    private static Claim claimWithCorners(UUID worldId, int lesserX, int lesserZ, int greaterX, int greaterZ) {
        World world = world(worldId);
        Claim claim = mock(Claim.class);
        when(claim.getLesserBoundaryCorner()).thenReturn(new Location(world, lesserX, 64, lesserZ));
        when(claim.getGreaterBoundaryCorner()).thenReturn(new Location(world, greaterX, 70, greaterZ));
        return claim;
    }

    private static World world(UUID worldId) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        return world;
    }

    private static Logger noOpLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }
}
