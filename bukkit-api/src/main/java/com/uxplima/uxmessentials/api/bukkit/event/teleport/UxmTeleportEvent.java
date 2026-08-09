package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/** What every teleport notification has in common: the player being moved, or the one whose request it is. */
@NullMarked
public abstract class UxmTeleportEvent extends UxmPlayerEvent {

    protected UxmTeleportEvent(UUID playerId, String playerName) {
        super(playerId, playerName);
    }
}
