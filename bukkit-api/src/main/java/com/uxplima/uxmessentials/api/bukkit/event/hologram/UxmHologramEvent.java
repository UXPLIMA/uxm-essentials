package com.uxplima.uxmessentials.api.bukkit.event.hologram;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every hologram notification has in common: which hologram, and which staff member changed it.
 *
 * <p>Holograms belong to the server, so the player named is the one who made the change.
 */
@NullMarked
public abstract class UxmHologramEvent extends UxmPlayerEvent {

    private final String hologramName;

    protected UxmHologramEvent(UUID actorId, String actorName, String hologramName) {
        super(actorId, actorName);
        this.hologramName = Objects.requireNonNull(hologramName, "hologramName");
    }

    /** The hologram's name, as typed in {@code /hologram}. */
    public String getHologramName() {
        return hologramName;
    }
}
