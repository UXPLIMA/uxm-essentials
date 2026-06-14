package com.uxplima.uxmessentials.staff.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A staff member left staff mode: their real loadout has been restored from the repository and the stored
 * row deleted. Published after the restore, so a consumer reading this event can trust the player is back
 * to their pre-mode state.
 *
 * @param staff the player who left staff mode
 */
public record StaffModeExited(PlayerRef staff) implements StaffEvent {

    public StaffModeExited {
        Objects.requireNonNull(staff, "staff");
    }
}
