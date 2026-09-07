package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The live player a {@link PlayerRef} names, when they are still on this server.
 *
 * <p>{@link BukkitRefs} maps a handle this side already holds and looks nothing up, which is what its javadoc
 * says and what keeps it pure. This is the other direction, and it is a lookup: a ref carries a uuid and a name
 * recorded at some earlier moment, and the player behind it may have logged out since.
 *
 * <p>It exists because uxmLib's menu engine takes a live {@code Player} and this plugin stores a ref in the state
 * that outlives one click. Empty means the same thing the engine used to mean when its own lookup missed: the
 * viewer left, so there is nothing to draw and nothing to say.
 */
@NullMarked
public final class LivePlayers {

    private LivePlayers() {}

    /** The player {@code ref} names, or empty when they are not online here. */
    public static Optional<Player> of(PlayerRef ref) {
        Objects.requireNonNull(ref, "ref");
        Player player = Bukkit.getPlayer(ref.uuid());
        return player != null && player.isOnline() ? Optional.of(player) : Optional.empty();
    }
}
