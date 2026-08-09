package com.uxplima.uxmessentials.api.bukkit.event.home;

import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What every vetoable homes event has in common: whose home it would be, and which slot.
 *
 * <p>Cancelling means the operation stops before anything is written and before the owner is charged for it, so a
 * refusal costs the player nothing and leaves no half-done state behind.
 */
@NullMarked
public abstract class UxmHomePreEvent extends UxmCancellableEvent {

    private final int slot;

    protected UxmHomePreEvent(UUID ownerId, String ownerName, int slot) {
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
