package com.uxplima.uxmessentials.api.bukkit.event.playerwarp;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every player-warp notification has in common: whose warp it is, and what it is called.
 *
 * <p>Unlike a server warp, this one has a real owner, and the player named is always that owner even when an admin
 * made the change on their behalf.
 */
@NullMarked
public abstract class UxmPlayerWarpEvent extends UxmPlayerEvent {

    private final String warpName;

    protected UxmPlayerWarpEvent(UUID ownerId, String ownerName, String warpName) {
        super(ownerId, ownerName);
        this.warpName = Objects.requireNonNull(warpName, "warpName");
    }

    /** The warp's name, as typed in {@code /pwarp}. */
    public String getWarpName() {
        return warpName;
    }
}
