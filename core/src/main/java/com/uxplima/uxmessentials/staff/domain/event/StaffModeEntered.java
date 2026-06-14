package com.uxplima.uxmessentials.staff.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A staff member entered staff mode: their real loadout has been committed to the repository and their
 * inventory swapped for the gadget hotbar. Published after the loadout is persisted, so a consumer reading
 * this event can trust the real loadout is already recoverable.
 *
 * @param staff the player who entered staff mode
 */
public record StaffModeEntered(PlayerRef staff) implements StaffEvent {

    public StaffModeEntered {
        Objects.requireNonNull(staff, "staff");
    }
}
