package com.uxplima.uxmessentials.api.bukkit.event.world;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every world notification has in common: which world.
 *
 * <p>These are the only uxmEssentials facts with no player at their centre. A world is created, loaded or deleted for
 * the server as a whole, so they are delivered on the global region rather than on anybody's own.
 */
@NullMarked
public abstract class UxmWorldEvent extends UxmEvent {

    private final String worldName;

    protected UxmWorldEvent(String worldName) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
    }

    /** The world's name, as the server knows it. */
    public String getWorldName() {
        return worldName;
    }
}
