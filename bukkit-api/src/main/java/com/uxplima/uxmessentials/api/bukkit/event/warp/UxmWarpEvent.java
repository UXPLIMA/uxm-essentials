package com.uxplima.uxmessentials.api.bukkit.event.warp;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every server-warp notification has in common: which warp, and who acted on it.
 *
 * <p>A warp belongs to the server rather than to a player, so the player named here is whoever performed the change,
 * not an owner. For a create that is the warp's recorded owner; for a delete it is whoever removed it.
 */
@NullMarked
public abstract class UxmWarpEvent extends UxmPlayerEvent {

    private final String warpName;

    protected UxmWarpEvent(UUID actorId, String actorName, String warpName) {
        super(actorId, actorName);
        this.warpName = Objects.requireNonNull(warpName, "warpName");
    }

    /** The warp's name, as typed in {@code /warp}. */
    public String getWarpName() {
        return warpName;
    }
}
