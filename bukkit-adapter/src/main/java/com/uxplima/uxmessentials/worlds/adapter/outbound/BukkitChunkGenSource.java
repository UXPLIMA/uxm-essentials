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
        @Nullable World w = server.getWorld(world.value());
        return w == null ? CompletableFuture.completedFuture(null) : w.getChunkAtAsync(chunkX, chunkZ);
    }
}
