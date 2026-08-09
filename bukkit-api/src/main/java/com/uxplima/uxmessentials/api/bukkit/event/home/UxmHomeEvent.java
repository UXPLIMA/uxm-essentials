package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every homes notification event has in common: whose home it is, and which slot.
 *
 * <p>The owner is the player the event is about, so {@code getPlayerId()} and the owner are the same person for
 * every event here: a home is only ever created, moved or deleted by its owner, and an admin acting on somebody
 * else's home acts as them.
 */
@NullMarked
public abstract class UxmHomeEvent extends UxmEvent {

    private final int slot;

    protected UxmHomeEvent(UUID ownerId, String ownerName, int slot) {
        super(ownerId, ownerName);
        this.slot = slot;
    }

    /** The home's slot, counting from zero: the index into the owner's grid, and its database key. */
    public int getSlot() {
        return slot;
    }

    /** The home's number as the player sees it in chat and menus, counting from one. */
    public int getSlotNumber() {
        return slot + 1;
    }
}
