package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerInfo} implementation for the read-only {@code /getpos} and {@code /ping} queries. It reads
 * the live player's location and round-trip latency without mutating anything and maps the location to the
 * kernel {@link Position}. The scan reads live entity state, so the caller runs it on the player's region
 * thread; an offline target yields an empty result.
 */
@NullMarked
public final class BukkitPlayerInfo implements PlayerInfo {

    @Override
    public Optional<Position> positionOf(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = online(who);
        if (player == null) {
            return Optional.empty();
        }
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player always has); guard it at the boundary.
        Location location = player.getLocation();
        return location == null ? Optional.empty() : Optional.of(BukkitRefs.toPosition(location));
    }

    @Override
    public Optional<Integer> pingOf(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = online(who);
        return player == null ? Optional.empty() : Optional.of(player.getPing());
    }

    private static @org.jspecify.annotations.Nullable Player online(PlayerRef who) {
        Player player = Bukkit.getPlayer(who.uuid());
        return player != null && player.isOnline() ? player : null;
    }
}
