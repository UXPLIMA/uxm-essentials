package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link ChunkGenSource}: it resolves the live {@code World} from the server and defers
 * to Paper's {@code World#getChunkAtAsync}, which generates the chunk off the main thread and completes
 * its future on a server thread. A world that is not loaded completes immediately with {@code null} so
 * the engine's job drains every remaining position and finishes rather than waiting on a chunk that can
 * never arrive.
 */
@NullMarked
public final class BukkitChunkGenSource implements ChunkGenSource {

    private final Server server;

    public BukkitChunkGenSource(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public CompletableFuture<?> generate(WorldName world, int chunkX, int chunkZ) {
        Objects.requireNonNull(world, "world");
        // Driven from the pregen job's repeatGlobal tick (the global region thread). getChunkAtAsync is the
        // Folia-safe async chunk primitive: it does not load/generate inline on the calling thread, it schedules
        // the work on the chunk's owning region and completes the future when ready, so requesting a far chunk
        // from the global thread is tolerated. If a future Folia build were to reject far-chunk requests from the
        // global region, the fallback is to wrap this call in scheduler.onRegion(chunkPosition, ...) so the request
        // originates on the owning region thread.
        @Nullable World w = server.getWorld(world.value());
        return w == null ? CompletableFuture.completedFuture(null) : w.getChunkAtAsync(chunkX, chunkZ);
    }
}
