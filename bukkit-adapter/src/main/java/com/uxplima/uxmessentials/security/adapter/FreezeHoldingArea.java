package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Moves a verifying player to a holding area and puts them back where they were once they verify.
 *
 * <p>The freeze already stops a player acting, but on some servers stopping them is not enough: wherever they logged
 * out is where they are still standing, in view of anyone who walks past, in a world whose mobs and other players
 * carry on around them. A holding area answers that by putting the unverified session somewhere deliberately empty
 * for its duration, which is also the honest place to be if the account turns out not to belong to whoever is at the
 * keyboard.
 *
 * <p>It is off by default and blank means "leave them where they are", because for most servers the freeze on its own
 * is the right amount of interference.
 *
 * <p>The return trip matters more than the outward one. Where the player was is remembered before they are moved, and
 * a failure to send them back would leave a verified player stranded in an empty room, so the origin is kept until it
 * is used and the move is only ever attempted for a player who actually went. Both legs are the module's own
 * teleports, so both are announced through {@link FreezeTeleports}: the freeze cancels teleports, and it must not
 * cancel these two.
 */
@NullMarked
public final class FreezeHoldingArea {

    private final @Nullable Location destination;
    private final FreezeTeleports ownTeleports;
    private final Logger log;

    /** Where each held player was standing when the freeze took them, so the verify can hand it back. */
    private final Map<UUID, Location> origins = new ConcurrentHashMap<>();

    /** Players currently in the holding area, kept apart from {@link #origins} so a missing origin is still tracked. */
    private final Set<UUID> held = ConcurrentHashMap.newKeySet();

    public FreezeHoldingArea(@Nullable Location destination, FreezeTeleports ownTeleports, Logger log) {
        this.destination = destination;
        this.ownTeleports = Objects.requireNonNull(ownTeleports, "ownTeleports");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Parse {@code world,x,y,z} or {@code world,x,y,z,yaw,pitch} into a location, or empty when the value is blank,
     * malformed, or names a world this server does not have. A bad value is a logged no-op rather than a startup
     * failure: a holding area is a preference, and losing it must not cost anyone the verification itself.
     */
    public static Optional<Location> parse(String raw, Logger log) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(log, "log");
        if (raw.isBlank()) {
            return Optional.empty();
        }
        // The explicit limit keeps trailing empty fields, so "world,1,2," is the four-field malformed value it looks
        // like rather than silently collapsing into a three-field one.
        String[] parts = raw.split(",", -1);
        if (parts.length != 4 && parts.length != 6) {
            log.warn("event=security_holding_area_malformed value={}", raw);
            return Optional.empty();
        }
        World world = Bukkit.getWorld(parts[0].strip());
        if (world == null) {
            log.warn("event=security_holding_area_unknown_world value={}", raw);
            return Optional.empty();
        }
        try {
            Location location = new Location(
                    world,
                    Double.parseDouble(parts[1].strip()),
                    Double.parseDouble(parts[2].strip()),
                    Double.parseDouble(parts[3].strip()));
            if (parts.length == 6) {
                location.setYaw(Float.parseFloat(parts[4].strip()));
                location.setPitch(Float.parseFloat(parts[5].strip()));
            }
            return Optional.of(location);
        } catch (NumberFormatException malformed) {
            log.warn("event=security_holding_area_malformed value={}", raw);
            return Optional.empty();
        }
    }

    /** Whether a holding area is configured at all; false makes every other method a no-op. */
    public boolean isConfigured() {
        return destination != null;
    }

    /** Remember where {@code player} is and move them to the holding area. Must run on their own region thread. */
    public void hold(Player player) {
        Objects.requireNonNull(player, "player");
        if (destination == null) {
            return;
        }
        UUID id = player.getUniqueId();
        origins.putIfAbsent(id, player.getLocation());
        held.add(id);
        if (!ownTeleports.teleport(player, destination)) {
            // They are still standing where they were, so there is nothing to hand back later. Forgetting the origin
            // now is what stops the verification from "returning" them to a place they never left.
            origins.remove(id);
            held.remove(id);
            log.warn("event=security_holding_area_move_failed player={}", player.getName());
        }
    }

    /**
     * Put {@code player} back where they were before the freeze, if they were moved. Called on a verify, and again on
     * a quit so a disconnect mid-verification does not leave them logged out inside the holding area.
     */
    public void release(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        Location origin = origins.remove(id);
        if (!held.remove(id) || origin == null) {
            return;
        }
        if (!ownTeleports.teleport(player, origin)) {
            log.warn("event=security_holding_area_return_failed player={}", player.getName());
        }
    }

    /** Drop every remembered origin, called on module stop. */
    public void clearAll() {
        origins.clear();
        held.clear();
    }
}
