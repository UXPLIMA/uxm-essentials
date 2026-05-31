package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NearbyPlayers} implementation for {@code /near}: the players within a radius of the viewer in the
 * same world, ordered nearest-first. It reads the viewer's live location and scans that world's players,
 * excluding the viewer themselves and anyone beyond the radius. The scan reads live entity positions, so the
 * caller runs it on the viewer's region thread; an offline viewer yields an empty list.
 */
@NullMarked
public final class BukkitNearbyPlayers implements NearbyPlayers {

    @Override
    public List<Nearby> within(PlayerRef viewer, int radius) {
        Objects.requireNonNull(viewer, "viewer");
        Player self = Bukkit.getPlayer(viewer.uuid());
        if (self == null || !self.isOnline()) {
            return List.of();
        }
        World world = self.getWorld();
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player always has); guard it at the boundary.
        Location origin = Objects.requireNonNull(self.getLocation(), "viewer location");
        double radiusSquared = (double) radius * radius;
        return world.getPlayers().stream()
                .filter(other -> !other.getUniqueId().equals(viewer.uuid()))
                .flatMap(other -> measure(origin, other).stream())
                .filter(measured -> measured.squared() <= radiusSquared)
                .sorted(Comparator.comparingDouble(Measured::squared))
                .map(Measured::toNearby)
                .toList();
    }

    private static Optional<Measured> measure(Location origin, Player other) {
        Location location = other.getLocation();
        if (location == null || !Objects.equals(location.getWorld(), origin.getWorld())) {
            return Optional.empty();
        }
        return Optional.of(new Measured(BukkitRefs.toRef(other), location.distanceSquared(origin)));
    }

    /** A nearby candidate carried with its squared distance so the filter and sort avoid repeated sqrt. */
    private record Measured(PlayerRef who, double squared) {

        Nearby toNearby() {
            return new Nearby(who, (int) Math.round(Math.sqrt(squared)));
        }
    }
}
