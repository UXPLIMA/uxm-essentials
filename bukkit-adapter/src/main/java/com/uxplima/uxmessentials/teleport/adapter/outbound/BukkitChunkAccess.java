package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ChunkAccess;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link ChunkAccess}: it resolves a random-teleport candidate column through Paper's
 * asynchronous chunk loading rather than the old synchronous {@code getHighestBlockYAt}/{@code getBlockAt}
 * that generated far chunks on the tick thread. The flow is:
 *
 * <ol>
 *   <li>Resolve the live world; an absent world completes the future empty at once (never orphaned — the
 *       bug that used to hang the refill loop when the world had unloaded mid-search).</li>
 *   <li>Record whether the target chunk was already resident, then call {@code World#getChunkAtAsync}, which
 *       loads/generates the chunk <em>off the main thread</em> and completes on the chunk's owning region
 *       thread. There is no synchronous chunk load on a tick thread.</li>
 *   <li>On completion, copy a {@link ChunkSnapshot} (max-block-Y + biome, no light) and read the candidate's
 *       facts from that copy — highest ground, biome, standing safety, landing block — so nothing touches the
 *       live chunk once the snapshot is taken.</li>
 *   <li><strong>Chunk-lifecycle ownership:</strong> if the probe is what pulled the chunk in, request its
 *       unload again. Probed-but-unserved chunks otherwise accumulate on the heap and in the region files —
 *       the leak the redesign set out to close. A chunk that was already resident is left to its own
 *       lifecycle. The winning location's chunk is reloaded later by the teleport itself.</li>
 * </ol>
 *
 * <p>Every failure path — absent world, a load that fails, a read that throws — completes the future with
 * {@link Optional#empty()} rather than exceptionally or not at all, honouring the {@link ChunkAccess}
 * contract the finder relies on.
 *
 * <p>The candidate build reads the highest non-air block by scanning the snapshot top-down rather than
 * calling {@code ChunkSnapshot#getHighestBlockYAt}: the scan runs entirely on the copy (so the live chunk is
 * free to unload immediately) and mirrors the previous engine's "highest non-empty block" semantics.
 */
@NullMarked
public final class BukkitChunkAccess implements ChunkAccess {

    private final Server server;
    private final Logger log;

    public BukkitChunkAccess(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public CompletableFuture<Optional<SafeCandidate>> probe(SafeSearchArea area, int blockX, int blockZ) {
        Objects.requireNonNull(area, "area");
        CompletableFuture<Optional<SafeCandidate>> result = new CompletableFuture<>();
        World world = server.getWorld(area.world().uid());
        if (world == null) {
            result.complete(Optional.empty());
            return result;
        }
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        boolean wasLoaded = world.isChunkLoaded(cx, cz);
        var ignored = world.getChunkAtAsync(cx, cz, true)
                .whenComplete((chunk, error) ->
                        complete(result, area.world(), world, chunk, error, blockX, blockZ, cx, cz, wasLoaded));
        return result;
    }

    /**
     * Complete {@code result} from the loaded chunk (or empty on failure) and, in a {@code finally}, release
     * the chunk if this probe is what loaded it — so the future always completes and no probed chunk leaks.
     */
    void complete(
            CompletableFuture<Optional<SafeCandidate>> result,
            WorldRef worldRef,
            World world,
            @Nullable Chunk chunk,
            @Nullable Throwable error,
            int blockX,
            int blockZ,
            int cx,
            int cz,
            boolean wasLoaded) {
        try {
            result.complete(candidateFrom(worldRef, world, chunk, error, blockX, blockZ));
        } catch (RuntimeException failure) {
            log.error("event=rtp_chunk_probe_failed", failure);
            result.complete(Optional.empty());
        } finally {
            releaseIfProbed(world, cx, cz, wasLoaded);
        }
    }

    private Optional<SafeCandidate> candidateFrom(
            WorldRef worldRef, World world, @Nullable Chunk chunk, @Nullable Throwable error, int blockX, int blockZ) {
        if (error != null) {
            log.error("event=rtp_chunk_probe_failed", error);
            return Optional.empty();
        }
        if (chunk == null) {
            return Optional.empty();
        }
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, true, false);
        return Optional.of(
                readCandidate(worldRef, snapshot, world.getMinHeight(), world.getMaxHeight(), blockX, blockZ));
    }

    /**
     * Build the pure candidate from the chunk snapshot copy: the highest non-air ground, the biome there, a
     * solid-non-liquid-ground-plus-two-air standing check, and the landing block. Reads only the snapshot, so
     * the live chunk is no longer needed once this runs.
     */
    SafeCandidate readCandidate(
            WorldRef worldRef, ChunkSnapshot snapshot, int minHeight, int maxHeight, int blockX, int blockZ) {
        int lx = blockX & 15;
        int lz = blockZ & 15;
        int groundY = highestNonAirY(snapshot, lx, lz, minHeight, maxHeight);
        Position position = new Position(worldRef, blockX + 0.5, groundY + 1.0, blockZ + 0.5, 0f, 0f);
        BiomeName biome =
                BiomeName.of(snapshot.getBiome(lx, groundY, lz).getKey().getKey());
        Material ground = materialAt(snapshot, lx, groundY, lz, minHeight, maxHeight);
        boolean standingSafe = standingSafe(snapshot, ground, lx, groundY, lz, minHeight, maxHeight);
        BlockTypeName landing = BlockTypeName.of(ground.getKey().getKey());
        return new SafeCandidate(position, biome, standingSafe, false, landing);
    }

    /** Request the chunk's unload only when our probe is what loaded it — the chunk-lifecycle ownership rule. */
    void releaseIfProbed(World world, int cx, int cz, boolean wasLoaded) {
        if (!wasLoaded) {
            world.unloadChunkRequest(cx, cz);
        }
    }

    private static boolean standingSafe(
            ChunkSnapshot snapshot, Material ground, int lx, int groundY, int lz, int minHeight, int maxHeight) {
        Material feet = materialAt(snapshot, lx, groundY + 1, lz, minHeight, maxHeight);
        Material head = materialAt(snapshot, lx, groundY + 2, lz, minHeight, maxHeight);
        return ground.isSolid() && !isLiquid(ground) && feet.isAir() && head.isAir();
    }

    private static int highestNonAirY(ChunkSnapshot snapshot, int lx, int lz, int minHeight, int maxHeight) {
        for (int y = maxHeight - 1; y >= minHeight; y--) {
            if (!snapshot.getBlockType(lx, y, lz).isAir()) {
                return y;
            }
        }
        return minHeight;
    }

    /** The block material at {@code y}, treating anything outside the world's height as open air (AIR). */
    private static Material materialAt(ChunkSnapshot snapshot, int lx, int y, int lz, int minHeight, int maxHeight) {
        if (y < minHeight || y >= maxHeight) {
            return Material.AIR;
        }
        return snapshot.getBlockType(lx, y, lz);
    }

    private static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }
}
