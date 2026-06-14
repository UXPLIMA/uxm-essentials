package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.ToggleVanish;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The presence-backed {@link StaffVanish}: it orchestrates the existing presence module's {@link ToggleVanish}
 * so staff mode never owns a second vanish state. The port asks for an <i>absolute</i> set (vanish on enter,
 * reveal on exit) but presence only exposes a toggle, so this reads the current vanished flag from the
 * {@link PresenceStore} and toggles only when the live state differs from the requested one — making the
 * absolute-set idempotent and leaving an already-correctly-vanished player untouched.
 *
 * <p>This impl is bound only when the presence module is enabled; with presence off the wiring binds
 * {@link StaffVanish#NONE} instead, so staff mode degrades to "vanish does nothing" rather than failing.
 */
@NullMarked
public final class PresenceStaffVanish implements StaffVanish {

    private final ToggleVanish toggleVanish;
    private final PresenceStore store;

    public PresenceStaffVanish(ToggleVanish toggleVanish, PresenceStore store) {
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public void setVanished(PlayerRef who, boolean vanished) {
        Objects.requireNonNull(who, "who");
        if (store.current(who).vanished() != vanished) {
            toggleVanish.toggle(who);
        }
    }
}
