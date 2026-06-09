package com.uxplima.uxmessentials.warps.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BukkitWarpSafetyChecker implements WarpSafetyChecker {

    @Override
    public boolean isSafe(Position position) {
        Objects.requireNonNull(position, "position");
        World world = Bukkit.getWorld(position.world().name());
        if (world == null) {
            return false;
        }

        Location loc = new Location(world, position.x(), position.y(), position.z());

        // Block underneath (legs block y - 1)
        Block ground = loc.clone().subtract(0, 1, 0).getBlock();
        if (!ground.getType().isSolid()) {
            return false;
        }
        String groundName = ground.getType().name();
        if (groundName.contains("LAVA") || groundName.contains("FIRE") || groundName.contains("MAGMA")) {
            return false;
        }

        // Leg level block (y)
        Block legs = loc.getBlock();
        if (legs.getType().isSolid()) {
            return false;
        }
        String legsName = legs.getType().name();
        if (legsName.contains("LAVA") || legsName.contains("FIRE")) {
            return false;
        }

        // Head level block (y + 1)
        Block head = loc.clone().add(0, 1, 0).getBlock();
        if (head.getType().isSolid()) {
            return false;
        }
        String headName = head.getType().name();
        if (headName.contains("LAVA") || headName.contains("FIRE")) {
            return false;
        }

        return true;
    }
}
