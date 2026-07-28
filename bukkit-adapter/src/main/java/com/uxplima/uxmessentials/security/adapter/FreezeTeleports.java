package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;

/**
 * The one exception to "a frozen player cannot be teleported": the module's own two moves, into the holding area and
 * back out of it.
 *
 * <p>The freeze cancels teleports precisely so nobody else can move a player who is not in a position to object, and
 * that guard cannot tell one caller from another: a teleport event carries a cause, not an author, and the plugin
 * cause is what every other plugin uses too. So rather than guess, the module says so out loud. It marks the player
 * immediately before its own teleport, the freeze listener consumes that mark and lets exactly one teleport through,
 * and anything else stays cancelled.
 *
 * <p>The mark is consumed rather than merely read, so it cannot be left standing as a hole somebody else's teleport
 * could fall into. It is also cleared if the teleport does not happen (a player who left in the meantime), so a stale
 * mark cannot survive to permit an unrelated move later.
 */
@NullMarked
public final class FreezeTeleports {

    /** Players whose very next teleport is one of ours. */
    private final Set<UUID> allowed = ConcurrentHashMap.newKeySet();

    /**
     * Teleport {@code player} to {@code destination} as the module's own move, exempt from the freeze's guard.
     *
     * @return whether the move was started; false means the exemption was withdrawn again and the player did not go
     */
    public boolean teleport(Player player, Location destination) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destination, "destination");
        UUID id = player.getUniqueId();
        allowed.add(id);
        try {
            // teleportAsync loads the destination chunk off the tick thread and is the Folia-correct move; the mark is
            // dropped afterwards either way, so a teleport that never lands leaves no standing exemption behind.
            var ignored = player.teleportAsync(destination).whenComplete((moved, failure) -> allowed.remove(id));
            return true;
        } catch (RuntimeException refused) {
            // The move is a convenience; the verification behind it is not. Withdrawing the mark here is the part that
            // matters: a refused teleport must not leave a standing exemption for somebody else's move to use.
            allowed.remove(id);
            return false;
        }
    }

    /** Whether {@code playerId}'s current teleport is one of ours, consuming the mark so it permits only this one. */
    public boolean consume(UUID playerId) {
        return allowed.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every outstanding mark, called on module stop. */
    public void clearAll() {
        allowed.clear();
    }
}
