package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerInfo} implementation for the read-only {@code /getpos}, {@code /ping}, and {@code /playtime}
 * queries. It reads the live player's location, round-trip latency, and total time played without mutating
 * anything and maps the location to the kernel {@link Position}. The scan reads live entity state, so the
 * caller runs it on the player's region thread; an offline target yields an empty result.
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

    @Override
    public Optional<Duration> playtimeOf(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = online(who);
        if (player == null) {
            return Optional.empty();
        }
        // PLAY_ONE_MINUTE is mis-named: the vanilla statistic counts ticks, so 1 tick = 50ms.
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Optional.of(Duration.ofMillis(ticks * 50L));
    }

    private static @org.jspecify.annotations.Nullable Player online(PlayerRef who) {
        Player player = Bukkit.getPlayer(who.uuid());
        return player != null && player.isOnline() ? player : null;
    }
}
