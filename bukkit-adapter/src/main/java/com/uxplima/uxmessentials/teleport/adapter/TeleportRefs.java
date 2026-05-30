package com.uxplima.uxmessentials.teleport.adapter;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Teleport-context boundary helpers over a live {@link Player}. Paper marks {@code Player#getLocation()}
 * {@code @Nullable} (it is null only for an entity with no world, which a connected player never is), so
 * reading a player's current {@link Position} is centralised here with the single non-null assertion
 * rather than repeated at every command and listener. Keeping it next to {@code BukkitRefs} keeps the
 * anti-corruption mapping in one obvious place per context.
 */
@NullMarked
public final class TeleportRefs {

    private TeleportRefs() {}

    /** The player's current {@link Position}; the location of a connected player is always present. */
    public static Position positionOf(Player player) {
        return BukkitRefs.toPosition(location(player));
    }

    /** The player's current {@link Location}, asserted non-null for a connected player. */
    public static Location location(Player player) {
        Objects.requireNonNull(player, "player");
        return Objects.requireNonNull(player.getLocation(), "player location");
    }
}
