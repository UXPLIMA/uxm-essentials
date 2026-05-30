package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerLocator} implementation. Reads the player's current location and maps it to a
 * {@link Position}; an offline player returns empty.
 *
 * <p>Reading a live location is region-bound work. The caller is expected to invoke this on the
 * player's region thread (the teleport context reads the {@code /back} capture and the warmup origin
 * from inside a region/entity task); the locator itself only translates the live handle.
 */
@NullMarked
public final class BukkitPlayerLocator implements PlayerLocator {

    @Override
    public Optional<Position> locate(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        Location location = player.getLocation();
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.of(BukkitRefs.toPosition(location));
    }
}
