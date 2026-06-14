package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled seam to the presence context: set a staff member's vanish state when they enter or leave
 * staff mode. Staff mode does not own vanish; it orchestrates the existing presence module's
 * {@code ToggleVanish}/{@code PresenceStore} through this port so there is no second vanish state to keep in
 * sync.
 *
 * <p>The coupling is soft: when the presence module is disabled (or has not yet landed) the wiring binds
 * {@link #NONE}, so staff mode degrades to "vanish does nothing" rather than failing — the same
 * degrade-when-the-other-module-is-off pattern as messaging's {@code MutePolicy.NEVER}.
 */
public interface StaffVanish {

    /** A no-op vanish — the binding when presence is disabled. */
    StaffVanish NONE = (who, vanished) -> {};

    /** Set {@code who}'s vanish state to {@code vanished}. */
    void setVanished(PlayerRef who, boolean vanished);
}
