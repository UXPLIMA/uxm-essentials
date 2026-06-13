package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * A Bukkit-side {@link SethomeGuard} that rejects placements in dangerous spots: a solid block at
 * feet or head (would suffocate), lava or fire directly below feet, or no solid ground within
 * {@code midairGroundDepth} blocks below (floating/mid-air, when the mid-air check is enabled).
 *
 * <p>An unloaded world is not blocked — the check cannot be authoritative when no blocks are loaded,
 * so it conservatively allows the placement. Any unexpected state is treated the same way.
 */
@NullMarked
public final class SafeLocationGuard implements SethomeGuard {

    private final Server server;
    private final boolean blockUnsafe;
    private final boolean considerMidair;
    private final int midairGroundDepth;

    public SafeLocationGuard(Server server, boolean blockUnsafe, boolean considerMidair, int midairGroundDepth) {
        this.server = Objects.requireNonNull(server, "server");
        this.blockUnsafe = blockUnsafe;
        this.considerMidair = considerMidair;
        this.midairGroundDepth = midairGroundDepth;
    }

    @Override
    public Result<Unit, HomeError> check(PlayerRef owner, HomeSlot slot, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(at, "at");
        if (!blockUnsafe) {
            return Result.ok();
        }
        World world = server.getWorld(at.world().uid());
        if (world == null) {
            // Cannot judge an unloaded world; conservatively allow.
            return Result.ok();
        }
        return isUnsafe(world, at) ? Result.err(HomeError.UNSAFE_LOCATION) : Result.ok();
    }

    private boolean isUnsafe(World world, Position at) {
        int x = at.blockX();
        int y = at.blockY();
        int z = at.blockZ();
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        // Reject if the player would be inside a solid block at feet or head (would suffocate).
        // isSolid() is used instead of isPassable() because MockBukkit does not implement isPassable().
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return true;
        }
        Block below = world.getBlockAt(x, y - 1, z);
        Material belowType = below.getType();
        // Reject lava or fire directly underfoot.
        if (belowType == Material.LAVA || belowType == Material.FIRE) {
            return true;
        }
        // Reject when floating with no solid ground within the configured depth.
        if (considerMidair && !hasSolidGroundBelow(world, x, y, z)) {
            return true;
        }
        return false;
    }

    private boolean hasSolidGroundBelow(World world, int x, int y, int z) {
        int minY = Math.max(world.getMinHeight(), y - midairGroundDepth);
        for (int scanY = y - 1; scanY >= minY; scanY--) {
            if (world.getBlockAt(x, scanY, z).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
