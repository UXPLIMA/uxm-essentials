package com.uxplima.uxmessentials.api.bukkit.event.staff;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A staff member entered or left staff mode.
 *
 * <p>Their inventory and state have already been swapped by the time this fires, so a listener reading the player
 * reads them as they now are.
 */
@NullMarked
public final class UxmStaffModeEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean entered;

    public UxmStaffModeEvent(UUID staffId, String staffName, boolean entered) {
        super(staffId, staffName);
        this.entered = entered;
    }

    /** Whether they are now in staff mode. */
    public boolean isEntered() {
        return entered;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
