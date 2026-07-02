package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * The queue's refill primitive: generate one random candidate in a world's {@link SafeSearchArea}, load
 * and inspect its column on the candidate's region thread (biome, ground safety), and judge it with the
 * pure {@link SafeSearchPolicy}. The region-bound inspection is bridged back to the caller through a
 * {@link CompletableFuture} the refill loop awaits off the tick thread — the policy never sees a Bukkit
 * type, the chunk work never runs on a tick thread.
 *
 * <p>The biome read uses Paper's chunk-load via {@code getHighestBlockYAt} which loads the column; the
 * candidate's resolved Y is the highest solid block plus one, and {@code standingSafe} is a cheap solid
 * ground + breathable-space check on that column.
 */
@NullMarked
public final class SafeSearchValidator {

    private final Scheduler scheduler;
    private final SafeSearchPolicy policy;
    private final Clock clock;
    private final Logger log;

    public SafeSearchValidator(Scheduler scheduler, SafeSearchPolicy policy, Clock clock, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Try one random candidate in {@code area}; the future completes empty when the candidate fails. */
    public CompletableFuture<Optional<RtpSafeLocation>> tryOne(SafeSearchArea area) {
        Objects.requireNonNull(area, "area");
        double[] xz = randomPoint(area);
        Position probe = Position.of(area.world(), xz[0], 320.0, xz[1]);
        CompletableFuture<Optional<RtpSafeLocation>> result = new CompletableFuture<>();
        scheduler.onRegion(probe, () -> completeGuarded(result, () -> inspect(area, xz[0], xz[1])));
        return result;
    }

    /**
     * Complete {@code result} with the probe's verdict, or empty if the region read threw. A candidate probe
     * touches live chunk/biome/block state; a throw there must still complete the future, or the refill loop
     * awaiting it would hang and lose that slot forever. A failed probe simply means "no safe spot here" — the
     * loop tries another candidate — so the throw is logged and the future completes empty, never orphaned.
     */
    void completeGuarded(
            CompletableFuture<Optional<RtpSafeLocation>> result, Supplier<Optional<RtpSafeLocation>> probe) {
        try {
            result.complete(probe.get());
        } catch (RuntimeException failure) {
            log.error("event=rtp_probe_failed", failure);
            result.complete(Optional.empty());
        }
    }

    private Optional<RtpSafeLocation> inspect(SafeSearchArea area, double x, double z) {
        World world = Bukkit.getWorld(area.world().uid());
        if (world == null) {
            return Optional.empty();
        }
        SafeCandidate candidate = readCandidate(area.world(), world, x, z);
        return policy.accept(area, candidate, clock.instant()).asValue();
    }

    private static SafeCandidate readCandidate(WorldRef worldRef, World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int groundY = world.getHighestBlockYAt(blockX, blockZ);
        Position position = new Position(worldRef, blockX + 0.5, groundY + 1.0, blockZ + 0.5, 0f, 0f);
        BiomeName biome =
                BiomeName.of(world.getBiome(blockX, groundY, blockZ).getKey().getKey());
        boolean standingSafe = standingSafe(world, blockX, groundY, blockZ);
        BlockTypeName landing = BlockTypeName.of(
                world.getBlockAt(blockX, groundY, blockZ).getType().getKey().getKey());
        return new SafeCandidate(position, biome, standingSafe, false, landing);
    }

    private static boolean standingSafe(World world, int x, int groundY, int z) {
        Block ground = world.getBlockAt(x, groundY, z);
        Block feet = world.getBlockAt(x, groundY + 1, z);
        Block head = world.getBlockAt(x, groundY + 2, z);
        return ground.getType().isSolid() && !ground.isLiquid() && feet.isEmpty() && head.isEmpty();
    }

    private static double[] randomPoint(SafeSearchArea area) {
        double max = area.maxRadius();
        double min = area.minRadius();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0, 2 * Math.PI);
        double radius = Math.sqrt(random.nextDouble(min * min, max * max));
        double x = area.centerX() + (radius * Math.cos(angle));
        double z = area.centerZ() + (radius * Math.sin(angle));
        return new double[] {x, z};
    }
}
