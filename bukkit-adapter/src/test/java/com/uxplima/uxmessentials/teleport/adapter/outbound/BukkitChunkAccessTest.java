package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Pins {@link BukkitChunkAccess}'s off-thread candidate build, the null-world orphan fix, and the
 * chunk-lifecycle cleanup.
 *
 * <p>MockBukkit v26 does not implement {@code World#getChunkAtAsync}, {@code ChunkSnapshot#getHighestBlockYAt},
 * or {@code World#unloadChunkRequest} (all throw {@code UnimplementedOperationException}), so the async load
 * and the request-unload call are exercised only against a live Paper server. Here the pieces that MockBukkit
 * <em>can</em> drive are pinned directly: the snapshot read (via {@link BukkitChunkAccess#readCandidate} over a
 * real loaded-chunk snapshot), the absent-world completion, and the unload <em>decision</em> (via a mocked
 * {@code World}, verifying the request is made only for a chunk the probe itself loaded).
 */
class BukkitChunkAccessTest {

    private ServerMock server;
    private BukkitChunkAccess access;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        access = new BukkitChunkAccess(server, new NoOpLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void probeOverAnAbsentWorldCompletesEmptyRatherThanOrphaning() {
        // The world unloaded (or never existed): the old engine left this future orphaned, hanging the refill
        // loop. It must now complete — empty — at once.
        WorldRef ghost = new WorldRef(UUID.randomUUID(), "ghost");
        SafeSearchArea area = new SafeSearchArea(ghost, 0.0, 0.0, 0.0, 1_000.0, 100_000.0);

        CompletableFuture<Optional<SafeCandidate>> result = access.probe(area, 0, 0);

        assertThat(result).isCompleted();
        assertThat(result.join()).isEmpty();
    }

    @Test
    void readCandidateResolvesTheHighestGroundFromTheSnapshotOffTheLiveChunk() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 40;
        int blockZ = 40;
        // Drop a solid block well above the flat surface so the top-down scan has an unambiguous highest ground.
        int groundY = world.getHighestBlockYAt(blockX, blockZ) + 20;
        world.getBlockAt(blockX, groundY, blockZ).setType(Material.STONE);
        ChunkSnapshot snapshot = world.getChunkAt(blockX >> 4, blockZ >> 4).getChunkSnapshot(true, true, false);
        WorldRef ref = new WorldRef(world.getUID(), world.getName());

        SafeCandidate candidate =
                access.readCandidate(ref, snapshot, world.getMinHeight(), world.getMaxHeight(), blockX, blockZ);

        assertThat(candidate.position().x()).isEqualTo(blockX + 0.5);
        assertThat(candidate.position().z()).isEqualTo(blockZ + 0.5);
        assertThat(candidate.y()).isEqualTo(groundY + 1.0);
        assertThat(candidate.standingSafe()).isTrue();
        assertThat(candidate.landing()).contains(BlockTypeName.of("stone"));
        assertThat(candidate.biome())
                .isEqualTo(BiomeName.of(
                        world.getBiome(blockX, groundY, blockZ).getKey().getKey()));
    }

    @Test
    void readCandidateOverALiquidSurfaceIsNotStandingSafe() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 8;
        int blockZ = 8;
        // A water surface as the highest block (an ocean top): the column has no solid, non-liquid ground.
        int surfaceY = world.getHighestBlockYAt(blockX, blockZ) + 20;
        world.getBlockAt(blockX, surfaceY, blockZ).setType(Material.WATER);
        ChunkSnapshot snapshot = world.getChunkAt(blockX >> 4, blockZ >> 4).getChunkSnapshot(true, true, false);
        WorldRef ref = new WorldRef(world.getUID(), world.getName());

        SafeCandidate candidate =
                access.readCandidate(ref, snapshot, world.getMinHeight(), world.getMaxHeight(), blockX, blockZ);

        assertThat(candidate.standingSafe()).isFalse();
        assertThat(candidate.landing()).contains(BlockTypeName.of("water"));
    }

    @Test
    void requestsUnloadOfAChunkTheProbeItselfLoaded() {
        World world = mock(World.class);

        access.releaseIfProbed(world, 3, -2, false);

        verify(world).unloadChunkRequest(3, -2);
    }

    @Test
    void leavesAnAlreadyResidentChunkAlone() {
        World world = mock(World.class);

        access.releaseIfProbed(world, 3, -2, true);

        verify(world, never()).unloadChunkRequest(anyInt(), anyInt());
    }

    private static final class NoOpLogger implements Logger {
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
